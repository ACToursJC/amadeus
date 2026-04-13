package com.apua.amadeus.repository;

import com.apua.amadeus.entity.Comision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComisionRepository extends JpaRepository<Comision, Long> {
    boolean existsByPnrId(String pnrId);
    boolean existsByPnrIdAndFacNumero(String pnrId, String facNumero);
    boolean existsByPnrIdAndConfirmationCode(String pnrId, String confirmationCode);
}