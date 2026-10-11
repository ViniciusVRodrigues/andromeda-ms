package andromeda.sei.ms.shared.infrastructure.persistence;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Conversions for the few hot reads that bypass the mapping converter and read raw BSON
 * ({@code MongoTemplate.getCollection(...).find(...)}): mapping tens of thousands of nested
 * documents through {@code MappingMongoConverter} costs far more than the query itself. Each
 * conversion mirrors what the mapped path does, so a raw read filters and returns the same values:
 * string ids are stored as {@code ObjectId} when they are valid hex, dates go through the template's
 * own conversions, decimals are {@code Decimal128}.
 */
public final class RawDocuments {

    private RawDocuments() {}

    /** The stored form of a String {@code @Id}: an {@code ObjectId} when valid hex, else the string. */
    public static Object storedId(String id) {
        return id != null && ObjectId.isValid(id) ? new ObjectId(id) : id;
    }

    public static List<Object> storedIds(Collection<String> ids) {
        return ids.stream().map(RawDocuments::storedId).toList();
    }

    /** The stored form of a {@code LocalDate} field, by the template's configured conversions. */
    public static Object storedDay(MongoTemplate template, LocalDate day) {
        return template.getConverter().convertToMongoType(day);
    }

    /** A stored day back as {@code LocalDate}, by the template's conversions (UTC when it has none). */
    public static LocalDate day(MongoTemplate template, Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof Date date) {
            LocalDate converted = template.getConverter().getConversionService().convert(date, LocalDate.class);
            return converted != null ? converted : date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        return null;
    }

    public static String id(Document document) {
        Object id = document.get("_id");
        return id == null ? null : id.toString();
    }

    public static BigDecimal decimal(Object value) {
        if (value == null) return null;
        if (value instanceof Decimal128 decimal) return decimal.bigDecimalValue();
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        if (value instanceof String text) return new BigDecimal(text);
        return null;
    }

    public static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    @SuppressWarnings("unchecked")
    public static List<String> strings(Object value) {
        return value instanceof List<?> list ? list.stream().map(o -> o == null ? null : o.toString()).toList()
                : List.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Document> documents(Object value) {
        return value instanceof List<?> list
                ? list.stream().filter(Document.class::isInstance).map(Document.class::cast).toList()
                : List.of();
    }
}
