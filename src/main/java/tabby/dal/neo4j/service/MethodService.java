package tabby.dal.neo4j.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tabby.config.GlobalConfiguration;
import tabby.dal.neo4j.repository.MethodRefRepository;
import tabby.util.FileUtils;


@Slf4j
@Service
public class MethodService {

    @Autowired
    private MethodRefRepository methodRefRepository;

    public void importMethodRef(){
        if(FileUtils.fileExists(GlobalConfiguration.METHODS_CACHE_PATH)){
            methodRefRepository.loadMethodRefFromCSV(
                    FileUtils.getWinPath(GlobalConfiguration.METHODS_CACHE_PATH));
        }
    }

    public MethodRefRepository getRepository(){
        return methodRefRepository;
    }
}
