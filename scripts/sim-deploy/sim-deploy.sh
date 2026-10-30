#!/usr/bin/env bash
# sim-deploy.sh — prova local do scripts/deploy-ms.sh (Docker Desktop + Git Bash).
#
# O que sobe: um registry local (127.0.0.1:5000) com um ms de mentira (app/) em
# três tags (v1, v2, broken), um nginx de teste (127.0.0.1:18080 -> andromeda-ms-blue:9090) e
# uma sonda que bate no nginx a cada 0,5 s durante cada deploy.
# Cenários: a) v1 -> v2 bom; b) blue novo quebrado (volta o anterior);
#           c) green quebrado (blue nunca para); d) sobras de execução interrompida.
# Uso:  bash scripts/sim-deploy/sim-deploy.sh
#       (as portas 9090, 9091, 5000 e 18080 precisam estar livres)
# Tudo o que o harness cria é removido no fim. O shim de docker (que quebra só o blue
# novo no cenário b) vive em um diretório temporário e só vale dentro do harness.
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
DEPLOY="${HERE}/../deploy-ms.sh"
REG="127.0.0.1:5000"
NGINX_PORT=18080
WORK="$(mktemp -d)"
REAL_DOCKER="$(command -v docker)"
export HEALTH_RETRIES=15 HEALTH_INTERVAL=2
NET_CREATED=false
PROBE_PID=""
FAILS=0

d() { "${REAL_DOCKER}" "$@"; }

cleanup() {
  [ -n "${PROBE_PID}" ] && kill "${PROBE_PID}" 2>/dev/null
  for c in andromeda-ms-blue andromeda-ms-blue-old andromeda-ms-green sim-nginx sim-registry; do
    d rm -f "${c}" >/dev/null 2>&1
  done
  [ "${NET_CREATED}" = true ] && d network rm andromeda-net >/dev/null 2>&1
  d rmi -f "${REG}/fake-ms:v1" "${REG}/fake-ms:v2" "${REG}/fake-ms:broken" >/dev/null 2>&1
  rm -rf "${WORK}"
}
trap cleanup EXIT

for p in 9090 9091 5000 ${NGINX_PORT}; do
  if netstat -ano 2>/dev/null | grep -qE "[:.]${p} .*LISTENING"; then
    echo "porta ${p} ocupada — abortando"; exit 2
  fi
done

# ---- infraestrutura ----
d network inspect andromeda-net >/dev/null 2>&1 || { d network create andromeda-net >/dev/null; NET_CREATED=true; }
d run -d --name sim-registry -p 127.0.0.1:5000:5000 registry:2 >/dev/null
for v in v1 v2; do d build -q -t "${REG}/fake-ms:${v}" --build-arg VERSION="${v}" "${HERE}/app" >/dev/null; done
d build -q -t "${REG}/fake-ms:broken" --build-arg VERSION=broken --build-arg BROKEN=down "${HERE}/app" >/dev/null
for t in v1 v2 broken; do d push -q "${REG}/fake-ms:${t}" >/dev/null; done
# MSYS_NO_PATHCONV + caminho do Windows: o Git Bash estraga o caminho do bind mount.
MSYS_NO_PATHCONV=1 "${REAL_DOCKER}" run -d --name sim-nginx -p 127.0.0.1:${NGINX_PORT}:80 \
  -v "$(cd "${HERE}" && { pwd -W 2>/dev/null || pwd; })/nginx.conf:/etc/nginx/conf.d/default.conf:ro" \
  --network andromeda-net nginx:alpine >/dev/null
echo "SIM=1" > "${WORK}/env"

# shim: no cenário b, o `docker run` do blue novo ganha SIM_HEALTH=down
mkdir "${WORK}/shim"
cat > "${WORK}/shim/docker" <<SHIM
#!/usr/bin/env bash
if [ "\${SIM_BREAK_BLUE:-}" = 1 ] && [ "\${1:-}" = run ]; then
  for a in "\$@"; do
    if [ "\$a" = andromeda-ms-blue ]; then
      shift; exec "${REAL_DOCKER}" run -e SIM_HEALTH=down "\$@"
    fi
  done
fi
exec "${REAL_DOCKER}" "\$@"
SHIM
chmod +x "${WORK}/shim/docker"

# ---- helpers ----
probe_start() {
  : > "${WORK}/probe"
  ( while true; do
      code=$(curl -s -o /dev/null -m 2 -w '%{http_code}' "http://127.0.0.1:${NGINX_PORT}/actuator/health")
      echo "$(date +%s.%N) ${code}" >> "${WORK}/probe"
      sleep 0.5
    done ) &
  PROBE_PID=$!
}
probe_stop() { kill "${PROBE_PID}" 2>/dev/null; wait "${PROBE_PID}" 2>/dev/null; PROBE_PID=""; }
# total de sondas, não-200 e janela de indisponibilidade (do primeiro ao último não-200)
probe_report() {
  awk '$2!=200{n++; if(!f)f=$1; l=$1} {t++} END{printf "probes=%d non200=%d outage=%.1fs", t, n+0, (n? l-f+0.5 : 0)}' "${WORK}/probe"
}
on_9090() { curl -s -m 3 "http://127.0.0.1:9090/"; }
via_nginx() { curl -s -m 3 "http://127.0.0.1:${NGINX_PORT}/"; }
run_deploy() { # $1=tag ; log em ${WORK}/log
  IMAGE="${REG}/fake-ms:$1" ENV_FILE="${WORK}/env" PATH="${WORK}/shim:${PATH}" bash "${DEPLOY}" >"${WORK}/log" 2>&1
}
check() { # descrição, status (0=ok)
  if [ "$2" -eq 0 ]; then echo "  OK   $1"; else echo "  FAIL $1"; FAILS=$((FAILS+1)); fi
}
exists() { d inspect "$1" >/dev/null 2>&1; }
absent() { ! exists "$1"; }
is() { [ "$1" = "$2" ]; }
keylog() { grep -E 'Deploy complete|is healthy|rolling|Rollback|PRODUÇÃO|Primeiro|Removendo|restaurando|Health check failed|Switching' "${WORK}/log" | sed 's/^/    | /'; }
reset() { for c in andromeda-ms-blue andromeda-ms-blue-old andromeda-ms-green; do d rm -f "$c" >/dev/null 2>&1; done; }

# ---- (a) primeiro deploy v1, depois v1 -> v2 ----
echo "== a) v1 primeiro deploy, v1 -> v2 =="
reset
run_deploy v1; rc=$?
check "primeiro deploy exit 0 (rc=${rc})" "${rc}"; keylog
is "$(on_9090)" v1; check "9090 serve v1" $?
probe_start
run_deploy v2; rc=$?
probe_stop
check "v1->v2 exit 0 (rc=${rc})" "${rc}"; keylog
is "$(on_9090)" v2; check "9090 serve v2" $?
is "$(via_nginx)" v2; check "nginx serve v2" $?
absent andromeda-ms-blue-old; check "sem blue-old" $?
absent andromeda-ms-green; check "sem green" $?
echo "  RESULT a: $(probe_report)"

# ---- (b) blue novo quebrado, green ok ----
echo "== b) blue novo quebrado (v2 -> v1 com o blue forçado a ficar DOWN) =="
probe_start
SIM_BREAK_BLUE=1 run_deploy v1; rc=$?
probe_stop
[ "${rc}" -eq 1 ]; check "exit 1 (rc=${rc})" $?; keylog
is "$(on_9090)" v2; check "9090 voltou para v2" $?
is "$(via_nginx)" v2; check "nginx serve v2" $?
absent andromeda-ms-blue-old; check "sem blue-old" $?
echo "  RESULT b: $(probe_report)"

# ---- (c) green quebrado ----
echo "== c) green quebrado (IMAGE=:broken) =="
started_before=$(d inspect -f '{{.State.StartedAt}}' andromeda-ms-blue)
probe_start
run_deploy broken; rc=$?
probe_stop
started_after=$(d inspect -f '{{.State.StartedAt}}' andromeda-ms-blue)
[ "${rc}" -eq 1 ]; check "exit 1 (rc=${rc})" $?; keylog
is "${started_before}" "${started_after}"; check "blue nunca parou (StartedAt igual)" $?
is "$(on_9090)" v2; check "9090 segue v2" $?
absent andromeda-ms-green; check "sem green" $?
echo "  RESULT c: $(probe_report)"
[ "$(awk '$2!=200{n++} END{print n+0}' "${WORK}/probe")" -eq 0 ]; check "zero não-200 pelo nginx" $?

# ---- (d) sobras ----
echo "== d1) blue ausente e blue-old presente (execução interrompida) =="
d rename andromeda-ms-blue andromeda-ms-blue-old; d stop -t 1 andromeda-ms-blue-old >/dev/null
run_deploy v1; rc=$?
check "exit 0 (rc=${rc})" "${rc}"; keylog
is "$(on_9090)" v1; check "9090 serve v1" $?
absent andromeda-ms-blue-old; check "sem blue-old" $?
echo "== d2) blue-old ao lado de um blue vivo =="
d run -d --name andromeda-ms-blue-old "${REG}/fake-ms:v2" >/dev/null
run_deploy v2; rc=$?
check "exit 0 (rc=${rc})" "${rc}"; keylog
is "$(on_9090)" v2; check "9090 serve v2" $?
absent andromeda-ms-blue-old; check "sem blue-old" $?

echo
if [ "${FAILS}" -eq 0 ]; then echo "SIM: tudo certo"; else echo "SIM: ${FAILS} verificação(ões) falharam"; fi
exit "${FAILS}"
