package org.eurekaka.bricks.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.eurekaka.bricks.common.model.OrderSide;
import org.eurekaka.bricks.common.util.Utils;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试json功能
 */
public class JavaJsonMain {
    public static void main(String[] args) throws Exception {
        JsonObj obj = new JsonObj(1, "ha");
        String objString = Utils.mapper.writeValueAsString(obj);
        System.out.println(objString);

        JsonObj obj1 = Utils.mapper.readValue(objString, new TypeReference<>() {});
        System.out.println(obj1);

        Map<String, Object> props = new HashMap<>();
        props.put("side", OrderSide.ALL);
        props.put("k1", 5.0);
        props.put("k2", 5);
        double c = 5;
        props.put("k3", c);
        long d = 1000;
        props.put("k4", d);
        String out1 = Utils.mapper.writeValueAsString(props);
        System.out.println(out1);
        Map<String, Object> obj2 = Utils.mapper.readValue(out1, new TypeReference<>() {});
        System.out.println(obj2);
//        long dd = (Long) obj2.get("k4");
        String dd = (String) obj2.get("k4");
    }

    /**
     * package scope is ok
     */
    static class JsonObj {

        public int id;
        public String name;

        public JsonObj() {
        }

        public JsonObj(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return "JsonObj{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    '}';
        }
    }
}
