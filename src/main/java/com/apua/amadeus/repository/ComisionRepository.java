package com.apua.amadeus.repository;

import com.apua.amadeus.entity.Comision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComisionRepository extends JpaRepository<Comision, Long> {
    // Busca si ya existe un registro con ese PNR
    boolean existsByPnrId(String pnrId);
}