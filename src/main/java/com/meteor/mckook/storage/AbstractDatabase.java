package com.meteor.mckook.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public abstract class AbstractDatabase implements Database {

    /**
     * 设置具体参数
     * @param preparedStatement
     * @param objects
     * @return
     */
    private PreparedStatement preparedStatementSetObject(PreparedStatement preparedStatement,List<Object> objects){
        if(objects==null) return preparedStatement;
        for (int i = 0; i < objects.size(); i++) {
            try {
                preparedStatement.setObject(i+1,objects.get(i));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return preparedStatement;
    }

    /**
     * 替换sql语句 {path}
     * @param sql
     * @param params
     * @return
     */
    private String putParams(String sql,Map<String,String> params){
        return params.entrySet().stream().reduce(sql, (r, entry) -> {
            return r.replace("{" + entry.getKey() + "}", entry.getValue());
        }, (r1, r2) -> r2);
    }


    /**
     * 执行更新sql语句
     *
     * @param sql            SQL语句，可能包含 {key} 格式的占位符
     * @param params         用于替换SQL语句中 {key} 占位符的参数映射
     * @param parameterValue PreparedStatement 的参数值列表
     * @return
     */
    public int executeUpdate(String sql, Map<String, String> params, List<Object> parameterValue) {
        // 如果 params 不为 null且不为空, 则替换SQL中的占位符
        if (params != null && !params.isEmpty()) {
            sql = putParams(sql, params);
        }
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            preparedStatementSetObject(ps, parameterValue);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("执行数据库更新操作失败: " + e.getMessage(), e);
        }
        return 1;
    }


    /**
     * 查询结果集
     * @param sql
     * @param params
     * @param parameterValue
     * @return
     */
    public <T> T executeQuery(String sql, Map<String,String> params, List<Object> parameterValue,
                                  Function<ResultSet,T> handler){
        if(params!=null) sql = putParams(sql,params);
        try(PreparedStatement preparedStatement = preparedStatementSetObject(getConnection().prepareStatement(sql),parameterValue);
            ResultSet resultSet = preparedStatement.executeQuery();)
        {
            return handler.apply(resultSet);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public boolean isConnected() {
        return false;
    }
}
