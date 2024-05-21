package org.edena.store.ignite;

import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.query.annotations.QuerySqlFunction;

public class CustomSqlFunctions {

    @QuerySqlFunction
    public static boolean binEquals(BinaryObject binObj1, BinaryObject binObj2) {
        return safeEquals(binObj1.deserialize(), binObj2.deserialize());
    }

    @QuerySqlFunction
    public static boolean binStringEquals(BinaryObject binObj, String string) {
        Object object = binObj.deserialize();
        return safeEquals((object != null) ? object.toString() : object, string);
    }

    @QuerySqlFunction
    public static boolean binNotEquals(BinaryObject binObj1, BinaryObject binObj2) {
        return !binEquals(binObj1, binObj2);
    }

    @QuerySqlFunction
    public static boolean binStringNotEquals(BinaryObject binObj, String value) {
        return !binStringEquals(binObj, value);
    }

    @QuerySqlFunction
    public static boolean binIn(BinaryObject binObj, BinaryObject ... binObjs) {
        Object object = binObj.deserialize();
        for (BinaryObject binObj2: binObjs) {
            if (safeEquals(object, binObj2.deserialize()))
                return true;
        }
        return false;
    }

    @QuerySqlFunction
    public static boolean binStringIn(BinaryObject binObj, String ... values) {
        Object object = binObj.deserialize();
        for (Object value: values) {
            if (safeEquals((object != null) ? object.toString() : object, value))
                return true;
        }
        return false;
    }

    @QuerySqlFunction
    public static boolean binNotIn(BinaryObject binObj, BinaryObject ... binObjs) {
        return !binIn(binObj, binObjs);
    }

    @QuerySqlFunction
    public static boolean binStringNotIn(BinaryObject binObj, String ... values) {
        return !binStringIn(binObj, values);
    }

    private static boolean safeEquals(Object obj1, Object obj2) {
        return (obj1 == null && obj2 == null) || (obj1 != null && obj2 != null && obj1.equals(obj2));
    }
}