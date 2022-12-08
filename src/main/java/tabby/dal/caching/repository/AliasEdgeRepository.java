package tabby.dal.caching.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import tabby.dal.caching.bean.edge.Alias;


public interface AliasEdgeRepository extends CrudRepository<Alias, String> {

    @Query(value = "CALL CSVWRITE(:path, 'SELECT * FROM ALIAS')", nativeQuery=true)
    void save2Csv(@Param("path") String path);

    @Query(value = "select count(*) from ALIAS", nativeQuery=true)
    int countAll();
}
