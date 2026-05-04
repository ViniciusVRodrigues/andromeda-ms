package andromeda.sei.ms.feature.role.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "cloned_from_role_id")
    private UUID clonedFromRoleId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private List<PermissionJpaEntity> permissions = new ArrayList<>();
}
