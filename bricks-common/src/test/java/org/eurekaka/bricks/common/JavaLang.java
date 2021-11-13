package org.eurekaka.bricks.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JavaLang {
    public static void main(String[] args) throws Exception {
        // double test
//        double a = 123.53;
//        double b = 100.0;
//        double c = 0.01;
//        System.out.println(a / b);
//        System.out.println(a / c);
        Class<?> clz = Class.forName("org.eurekaka.bricks.common.C2");
        List<String> a1 = new ArrayList<>();
        a1.add("a1");
        System.out.println(a1.getClass());
        C1 out = (C1) clz.getConstructor(a1.getClass()).newInstance(a1);
        System.out.println(out);
    }
}
