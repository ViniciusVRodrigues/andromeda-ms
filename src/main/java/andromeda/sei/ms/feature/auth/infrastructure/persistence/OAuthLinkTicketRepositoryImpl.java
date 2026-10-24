package andromeda.sei.ms.feature.auth.infrastructure.persistence;

import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkTicket;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OAuthLinkTicketRepositoryImpl implements OAuthLinkTicketRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public OAuthLinkTicket save(OAuthLinkTicket ticket) {
        return toDomain(mongoTemplate.save(toDocument(ticket)));
    }

    @Override
    public Optional<OAuthLinkTicket> consume(String ticketHash, OAuthLinkPurpose purpose, Instant now) {
        Query query = new Query(Criteria.where("ticketHash").is(ticketHash)
                .and("purpose").is(purpose.name())
                .and("consumedAt").is(null)
                .and("expiresAt").gt(now));
        Update update = new Update().set("consumedAt", now).set("updatedAt", now).inc("version", 1);
        OAuthLinkTicketDocument doc = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), OAuthLinkTicketDocument.class);
        return Optional.ofNullable(doc).map(this::toDomain);
    }

    private OAuthLinkTicket toDomain(OAuthLinkTicketDocument d) {
        return new OAuthLinkTicket(d.getId(), d.getOrganizationId(), d.getUserId(), d.getTicketHash(),
                OAuthLinkPurpose.valueOf(d.getPurpose()), d.getAzureOrgUrl(), d.getExpiresAt(),
                d.getConsumedAt(), d.getCreatedAt());
    }

    private OAuthLinkTicketDocument toDocument(OAuthLinkTicket t) {
        return OAuthLinkTicketDocument.builder()
                .id(t.getId())
                .organizationId(t.getOrganizationId())
                .userId(t.getUserId())
                .ticketHash(t.getTicketHash())
                .purpose(t.getPurpose().name())
                .azureOrgUrl(t.getAzureOrgUrl())
                .expiresAt(t.getExpiresAt())
                .consumedAt(t.getConsumedAt())
                .active(true)
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getCreatedAt())
                .build();
    }
}
