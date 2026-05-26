package com.dacphu.manga.createseries_demo.entity;

import com.dacphu.manga.createseries_demo.model.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "[user]")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @Column(unique = true)
    private String email;

    private String username;

    private String passwordHash;

    private String displayName;

    @Enumerated(EnumType.STRING)
    private Role role;
}
