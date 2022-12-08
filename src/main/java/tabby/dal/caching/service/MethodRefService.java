package tabby.dal.caching.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tabby.dal.caching.bean.ref.MethodReference;
import tabby.dal.caching.repository.MethodRepository;
import tabby.config.GlobalConfiguration;

import java.util.List;


@Slf4j
@Service
public class MethodRefService {

    @Autowired
    private MethodRepository methodRepository;

    public MethodReference getMethodRefBySignature(String signature){
        return methodRepository.findMethodReferenceBySignature(signature);
    }

    public void clearCache(){
        log.info("All methods cache cleared!");
    }

    public void save(MethodReference ref){
        methodRepository.save(ref);
    }

    public void save(Iterable<MethodReference> refs){
        methodRepository.saveAll(refs);
    }

    public void save2Csv(){
        methodRepository.save2Csv(GlobalConfiguration.METHODS_CACHE_PATH);
    }

    public List<MethodReference> loadNecessaryMethodRefs(){
        return methodRepository.findAllNecessaryMethodRefs();
    }

    public int countAll(){
        return methodRepository.countAll();
    }
}
