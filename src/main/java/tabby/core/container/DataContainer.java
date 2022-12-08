package tabby.core.container;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import tabby.core.scanner.ClassInfoScanner;
import tabby.dal.caching.bean.edge.*;
import tabby.dal.caching.bean.ref.ClassReference;
import tabby.dal.caching.bean.ref.MethodReference;
import tabby.dal.caching.service.ClassRefService;
import tabby.dal.caching.service.MethodRefService;
import tabby.dal.caching.service.RelationshipsService;
import tabby.dal.neo4j.service.ClassService;
import tabby.dal.neo4j.service.MethodService;
import tabby.util.SemanticHelper;

import java.util.*;


@Getter
@Setter
@Slf4j
@Component
public class DataContainer {

    @Autowired
    private RulesContainer rulesContainer;

    @Autowired
    private ClassService classService;

    @Autowired
    private MethodService methodService;

    @Autowired
    private ClassRefService classRefService;
    @Autowired
    private MethodRefService methodRefService;
    @Autowired
    private RelationshipsService relationshipsService;

    //    private Map<String, ClassReference> savedClassRefs = new HashMap<>();
    private Map<String, ClassReference> savedClassRefs = Collections.synchronizedMap(new HashMap<>());
    //    private Map<String, MethodReference> savedMethodRefs = new HashMap<>();
    private Map<String, MethodReference> savedMethodRefs = Collections.synchronizedMap(new HashMap<>());

    private Set<Has> savedHasNodes = Collections.synchronizedSet(new HashSet<>());
    private Set<Call> savedCallNodes = Collections.synchronizedSet(new HashSet<>());
    private Set<Alias> savedAliasNodes = Collections.synchronizedSet(new HashSet<>());
    private Set<Extend> savedExtendNodes = Collections.synchronizedSet(new HashSet<>());
    private Set<Interfaces> savedInterfacesNodes = Collections.synchronizedSet(new HashSet<>());


    public void save(String type){
        switch (type){
            case "class":
                if(!savedClassRefs.isEmpty()){
                    List<ClassReference> list = new ArrayList<>(savedClassRefs.values());
                    savedClassRefs.clear();
                    classRefService.save(list);
                }
                break;
            case "method":
                if(!savedMethodRefs.isEmpty()){
                    List<MethodReference> list = new ArrayList<>(savedMethodRefs.values());
                    savedMethodRefs.clear();
                    methodRefService.save(list);
                }
                break;
            case "has":
                if(!savedHasNodes.isEmpty()){
                    relationshipsService.saveAllHasEdges(savedHasNodes);
                    savedHasNodes.clear();
                }
                break;
            case "call":
                if(!savedCallNodes.isEmpty()){
                    relationshipsService.saveAllCallEdges(savedCallNodes);
                    savedCallNodes.clear();
                }
                break;
            case "extend":
                if(!savedExtendNodes.isEmpty()){
                    relationshipsService.saveAllExtendEdges(savedExtendNodes);
                    savedExtendNodes.clear();
                }
                break;
            case "interfaces":
                if(!savedInterfacesNodes.isEmpty()){
                    relationshipsService.saveAllInterfacesEdges(savedInterfacesNodes);
                    savedInterfacesNodes.clear();
                }
                break;
            case "alias":
                if(!savedAliasNodes.isEmpty()){
                    relationshipsService.saveAllAliasEdges(savedAliasNodes);
                    savedAliasNodes.clear();
                }
                break;
        }
    }


    public <T> void store(T ref) {
        if(ref == null) return;

        if(ref instanceof ClassReference){
            ClassReference classRef = (ClassReference) ref;
            savedClassRefs.put(classRef.getName(), classRef);
        }else if(ref instanceof MethodReference){
            MethodReference methodRef = (MethodReference) ref;
            savedMethodRefs.put(methodRef.getSignature(), methodRef);
        }else if(ref instanceof Has){
            savedHasNodes.add((Has) ref);
        }else if(ref instanceof Call){
            savedCallNodes.add((Call) ref);
        }else if(ref instanceof Interfaces){
            savedInterfacesNodes.add((Interfaces) ref);
        }else if(ref instanceof Extend){
            savedExtendNodes.add((Extend) ref);
        }else if(ref instanceof Alias){
            savedAliasNodes.add((Alias) ref);
        }
    }


    public ClassReference getClassRefByName(String name){
        ClassReference ref = savedClassRefs.getOrDefault(name, null);
        if(ref != null) return ref;
        // find from h2
        ref = classRefService.getClassRefByName(name);
        return ref;
    }


    public MethodReference getMethodRefBySubSignature(String classname, String subSignature){
        String signature = String.format("<%s: %s>", clean(classname), clean(subSignature));
        MethodReference ref = savedMethodRefs.getOrDefault(signature, null);
        if(ref != null) return ref;
        // find from h2
        ref = methodRefService.getMethodRefBySignature(signature);
        return ref;
    }

    private String clean(String data){
        return data.replace("'", "");
    }


    public MethodReference getMethodRefBySignature(String signature){
        MethodReference ref = savedMethodRefs.getOrDefault(signature, null);
        if(ref != null) return ref;
        // find from h2
        ref = methodRefService.getMethodRefBySignature(signature);
        return ref;
    }


    public MethodReference getMethodRefBySignature(SootMethodRef sootMethodRef){
        SootClass cls = sootMethodRef.getDeclaringClass();
        String subSignature = sootMethodRef.getSubSignature().toString();
        MethodReference target
                = getMethodRefBySubSignature(cls.getName(), subSignature);
        if(target != null){
            return target;
        }

        return getFirstMethodRefFromFatherNodes(cls, subSignature, false);
    }


    public MethodReference getMethodRefBySignature(String classname, String subSignature){
        try{
            SootClass cls = Scene.v().getSootClass(classname);
            try{
                SootMethod method = cls.getMethod(subSignature);
                if(method != null){
                    return getMethodRefBySignature(method.makeRef());
                }
            }catch (Exception e){

                return getFirstMethodRefFromFatherNodes(cls, subSignature, false);
            }
        }catch (Exception e){

        }
        return null;
    }


    public MethodReference getOrAddMethodRef(SootMethodRef sootMethodRef, SootMethod method){

        MethodReference methodRef = getMethodRefBySignature(sootMethodRef);

        if(methodRef == null){

            SootClass cls = sootMethodRef.getDeclaringClass();
            ClassReference classRef = getClassRefByName(cls.getName());
            if(classRef == null){
                classRef = ClassInfoScanner.collect0(cls.getName(), cls, this, 0);
                methodRef = getMethodRefBySignature(sootMethodRef);
            }

            if(methodRef == null){
                methodRef = MethodReference.newInstance(classRef.getName(), method);
                Has has = Has.newInstance(classRef, methodRef);
                if(!classRef.getHasEdge().contains(has)){
                    classRef.getHasEdge().add(has);
                    store(has);
                    ClassInfoScanner.makeAliasRelation(has, this);
                }
                store(methodRef);
            }
        }
        return methodRef;
    }

    public MethodReference getFirstMethodRef(String classname, String subSignature){
        MethodReference target = getMethodRefBySubSignature(classname, subSignature);
        if(target != null) return target;

        SootClass sc = SemanticHelper.getSootClass(clean(classname));
        if(sc != null && sc.hasSuperclass()){
            target = getFirstMethodRef(sc.getSuperclass().getName(), subSignature);
        }
        return target;
    }


    public MethodReference getFirstMethodRefFromFatherNodes(SootClass cls, String subSignature, boolean deepFirst){

        MethodReference target = null;

        if(cls.hasSuperclass()){
            SootClass superCls = cls.getSuperclass();
            target = getTargetMethodRef(superCls, subSignature, deepFirst);

            if(target != null){
                return target;
            }
        }

        if(cls.getInterfaceCount() > 0){
            for(SootClass intface:cls.getInterfaces()){
                target = getTargetMethodRef(intface, subSignature, deepFirst);

                if(target != null){
                    return target;
                }
            }
        }
        return null;
    }

    public Set<MethodReference> getAliasMethodRefs(SootClass cls, String subSignature){
        Set<MethodReference> refs = new HashSet<>();
        Set<SootClass> classes = new HashSet<>();

        if(cls.hasSuperclass()){
            classes.add(cls.getSuperclass());
        }

        if(cls.getInterfaceCount() > 0){
            classes.addAll(cls.getInterfaces());
        }

        MethodReference ref = null;

        for(SootClass clazz:classes){
            ref = getMethodRefBySubSignature(clazz.getName(), subSignature);
            if(ref != null){
                refs.add(ref);
            }else{
                refs.addAll(getAliasMethodRefs(clazz, subSignature));
            }
        }
        return refs;
    }

    private MethodReference getTargetMethodRef(SootClass cls, String subSignature, boolean deepFirst){
        MethodReference target = null;
        if(deepFirst){
            target = getFirstMethodRefFromFatherNodes(cls, subSignature, deepFirst);
            if(target == null){
                target = getMethodRefBySubSignature(cls.getName(), subSignature);
            }
        }else{
            target = getMethodRefBySubSignature(cls.getName(), subSignature);
            if(target == null){
                target = getFirstMethodRefFromFatherNodes(cls, subSignature, deepFirst);
            }
        }

        return target;
    }

    public void loadNecessaryMethodRefs(){
        List<MethodReference> refs = methodRefService.loadNecessaryMethodRefs();
        refs.forEach(ref ->{
            savedMethodRefs.put(ref.getSignature(), ref);
        });
    }

    public void loadNecessaryClassRefs(){
        List<ClassReference> refs = classRefService.loadNecessaryClassRefs();
        refs.forEach(ref ->{
            savedClassRefs.put(ref.getName(), ref);
        });
    }

    public void save2Neo4j(){
        int nodes = classRefService.countAll() + methodRefService.countAll();
        int relations = relationshipsService.countAll();
        log.info("Total nodes: {}, relations: {}", nodes, relations);
        log.info("Clean old tabby.core.data in Neo4j.");
        classService.clear();
        log.info("Save methods to Neo4j.");
        methodService.importMethodRef();
        log.info("Save classes to Neo4j.");
        classService.importClassRef();
        log.info("Save relation to Neo4j.");
        classService.buildEdge();
    }

    public void save2CSV(){
        log.info("Save cache to CSV.");
        classRefService.save2Csv();
        methodRefService.save2Csv();
        relationshipsService.save2CSV();
        log.info("Save cache to CSV. DONE!");
    }

}