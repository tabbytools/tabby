package tabby.dal.caching.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import tabby.dal.caching.bean.ref.ClassReference;

import java.util.List;


public interface ClassRepository extends CrudRepository<ClassReference, String> {

    @Query(value = "select * from CLASSES where NAME = :name limit 1", nativeQuery = true)
    ClassReference findClassReferenceByName(String name);

    @Query(value="select count(*) from CLASSES", nativeQuery=true)
    int countAll();

    @Query(value = "CALL CSVWRITE(:path, 'SELECT * FROM CLASSES')", nativeQuery=true)
    void save2Csv(@Param("path") String path);

    @Query(value = "select * from CLASSES where NAME like 'sun.%' or NAME like 'java.%'", nativeQuery = true)
    List<ClassReference> findAllNecessaryClassRefs();

}
