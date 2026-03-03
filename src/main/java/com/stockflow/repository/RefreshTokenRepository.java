package com.stockflow.repository;

import com.stockflow.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByUsuarioId(Long usuarioId);

    void deleteByExpiracionBefore(LocalDateTime fecha);

    List<RefreshToken> findByUsuarioId(Long usuarioId);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revocado = true WHERE rt.token = :token")
    void revocarToken(@Param("token") String token);
}
