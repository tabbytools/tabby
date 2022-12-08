package tabby.dal.caching.converter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;


@Converter
public class SetInteger2JsonStringConverter implements AttributeConverter<Set<Integer>,String> {

    private static Gson gson = new Gson();

    @Override
    public String convertToDatabaseColumn(Set<Integer> attribute) {
        if(attribute == null){
            return "[]";
        }
        return gson.toJson(attribute);
    }

    @Override
    public Set<Integer> convertToEntityAttribute(String dbData) {
        if(dbData == null || "".equals(dbData)){
            return new HashSet<>();
        }
        Type objectType = new TypeToken<Set<Integer>>(){}.getType();
        return gson.fromJson(dbData, objectType);
    }
}
