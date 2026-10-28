package andromeda.sei.ms.feature.workitem.domain;

import java.util.List;

/**
 * One level of the root list: all roots sharing a rank (backlog-arvore-nivel D3).
 *
 * @param rank      the rank, {@code null} for the "no level" bucket
 * @param rawTypes  distinct raw types present at this rank, alphabetical
 * @param count     how many items of the whole result (all pages) are at this rank
 */
public record BacklogLevel(Integer rank, List<String> rawTypes, long count) {
}
