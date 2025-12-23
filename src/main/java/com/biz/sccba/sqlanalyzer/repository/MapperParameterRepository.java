package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.model.MapperParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MapperParameterRepository extends JpaRepository<MapperParameter, String> {
    
    /**
     * 根据Mapper ID查找参数
     */
    Optional<MapperParameter> findByMapperId(String mapperId);
}
