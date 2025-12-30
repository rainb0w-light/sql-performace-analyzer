package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.model.MapperParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MapperParameterRepository extends JpaRepository<MapperParameter, Long> {
    
    /**
     * 根据Mapper ID查找参数（返回第一个，用于兼容旧代码）
     */
    Optional<MapperParameter> findByMapperId(String mapperId);
    
    /**
     * 根据Mapper ID查找所有参数
     */
    List<MapperParameter> findAllByMapperId(String mapperId);
    
    /**
     * 根据Mapper ID删除所有参数
     */
    void deleteAllByMapperId(String mapperId);
}
