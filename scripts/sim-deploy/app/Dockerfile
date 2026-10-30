FROM python:3-alpine
ARG VERSION=dev
ARG BROKEN=up
ENV SIM_VERSION=$VERSION SIM_HEALTH=$BROKEN
COPY server.py /server.py
CMD ["python", "-u", "/server.py"]
