package cn.tenmg.sqltool.transaction;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import cn.tenmg.dsl.Script;
import cn.tenmg.dsl.utils.CollectionUtils;
import cn.tenmg.dsql.DSQLFactory;
import cn.tenmg.dsql.NamedSQL;
import cn.tenmg.sql.paging.utils.JDBCUtils;
import cn.tenmg.sqltool.exception.IllegalCallException;
import cn.tenmg.sqltool.exception.IllegalConfigException;
import cn.tenmg.sqltool.exception.SQLExecutorException;
import cn.tenmg.sqltool.sql.DML;
import cn.tenmg.sqltool.sql.SQLDialect;
import cn.tenmg.sqltool.sql.SQLExecuter;
import cn.tenmg.sqltool.sql.executer.ExecuteSQLExecuter;
import cn.tenmg.sqltool.sql.executer.ExecuteUpdateSQLExecuter;
import cn.tenmg.sqltool.sql.executer.GetSQLExecuter;
import cn.tenmg.sqltool.sql.executer.SelectSQLExecuter;
import cn.tenmg.sqltool.sql.parser.DeleteDMLParser;
import cn.tenmg.sqltool.sql.parser.GetDMLParser;
import cn.tenmg.sqltool.sql.parser.InsertDMLParser;
import cn.tenmg.sqltool.sql.utils.EntityUtils;
import cn.tenmg.sqltool.utils.JDBCExecuteUtils;
import cn.tenmg.sqltool.utils.SQLDialectUtils;

/**
 * 自定义事务执行器
 * 
 * @author June wjzhao@aliyun.com
 * 
 * @since 1.1.0
 */
public class CustomTransactionExecutor implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1728127905781636407L;

	private static ThreadLocal<SQLDialect> currentSQLDialect = new ThreadLocal<SQLDialect>();

	private DSQLFactory DSQLFactory;

	private boolean showSql = true;

	private int defaultBatchSize = 500;

	public DSQLFactory getDSQLFactory() {
		return DSQLFactory;
	}

	public void setDSQLFactory(DSQLFactory DSQLFactory) {
		this.DSQLFactory = DSQLFactory;
	}

	public boolean isShowSql() {
		return showSql;
	}

	public void setShowSql(boolean showSql) {
		this.showSql = showSql;
	}

	public int getDefaultBatchSize() {
		return defaultBatchSize;
	}

	public void setDefaultBatchSize(int defaultBatchSize) {
		this.defaultBatchSize = defaultBatchSize;
	}

	/**
	 * 开始事务
	 * 
	 * @param options
	 *            数据库配置
	 */
	public void beginTransaction(Map<String, String> options) {
		currentSQLDialect.set(SQLDialectUtils.getSQLDialect(options));
		Connection con = null;
		try {
			Class.forName(options.get("driver"));
			con = DriverManager.getConnection(options.get("url"), options.get("user"), options.get("password"));
			con.setAutoCommit(false);
			CurrentConnectionHolder.set(con);
		} catch (SQLException e) {
			JDBCUtils.close(con);
			throw new SQLExecutorException(e);
		} catch (ClassNotFoundException e) {
			throw new IllegalConfigException(e);
		}
	}

	/**
	 * 插入操作。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param obj
	 *            实体对象（不能为null）
	 * @return 返回受影响行数
	 * @throws SQLException
	 *             SQL异常
	 */
	public <T extends Serializable> int insert(T obj) throws SQLException {
		DML dml = InsertDMLParser.getInstance().parse(obj.getClass());
		List<Object> params = EntityUtils.getParams(obj, dml.getFields());
		return JDBCExecuteUtils.execute(CurrentConnectionHolder.get(), ExecuteUpdateSQLExecuter.getInstance(), null,
				dml.getSql(), params, showSql);
	}

	/**
	 * 插入操作（实体对象集为空则直接返回null）。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param rows
	 *            实体对象集
	 * @return 返回受影响行数
	 * @throws SQLException
	 *             SQL异常
	 */
	public <T extends Serializable> int insert(List<T> rows) throws SQLException {
		return JDBCExecuteUtils.executeBatch(CurrentConnectionHolder.get(), InsertDMLParser.getInstance(), rows,
				showSql);
	}

	/**
	 * 软保存。仅对属性值不为null的字段执行插入/更新操作。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param obj
	 *            实体对象
	 * @return 返回受影响行数
	 * @throws SQLException
	 *             SQL异常
	 */
	public <T extends Serializable> int save(T obj) throws SQLException {
		Script<List<Object>> sql = currentSQLDialect.get().save(obj);
		return JDBCExecuteUtils.execute(CurrentConnectionHolder.get(), ExecuteUpdateSQLExecuter.getInstance(), null,
				sql.getValue(), sql.getParams(), showSql);
	}

	/**
	 * 部分硬保存。仅对属性值不为null或硬保存的字段执行插入/更新操作。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param obj
	 *            实体对象
	 * @param hardFields
	 *            硬保存属性
	 * @return 返回受影响行数
	 * @throws SQLException
	 *             SQL异常
	 */
	public <T extends Serializable> int save(T obj, String... hardFields) throws SQLException {
		Script<List<Object>> sql = currentSQLDialect.get().save(obj, hardFields);
		return JDBCExecuteUtils.execute(CurrentConnectionHolder.get(), ExecuteUpdateSQLExecuter.getInstance(), null,
				sql.getValue(), sql.getParams(), showSql);
	}

	/**
	 * 软保存。仅对属性值不为null的字段执行插入/更新操作。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param rows
	 *            实体对象集
	 * @return 返回受影响行数
	 * @throws SQLException
	 *             SQL异常
	 */
	public <T extends Serializable> int save(List<T> rows) throws SQLException {
		if (CollectionUtils.isEmpty(rows)) {
			return 0;
		}
		return JDBCExecuteUtils.save(CurrentConnectionHolder.get(),
				currentSQLDialect.get().save(rows.get(0).getClass()), rows, showSql);
	}

	/**
	 * 部分硬保存。仅对属性值不为null或硬保存的字段执行插入/更新操作。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param rows
	 *            实体对象集
	 * @param hardFields
	 *            硬保存属性
	 * @return 返回受影响行数
	 * @throws SQLException
	 *             SQL异常
	 */
	public <T extends Serializable> int save(List<T> rows, String... hardFields) throws SQLException {
		if (CollectionUtils.isEmpty(rows)) {
			return 0;
		}
		return JDBCExecuteUtils.save(CurrentConnectionHolder.get(),
				currentSQLDialect.get().save(rows.get(0).getClass(), hardFields), rows, showSql);
	}

	/**
	 * 硬保存。对所有字段执行插入/更新操作。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param obj
	 *            实体对象
	 * @return 返回受影响行数
	 * @throws SQLException
	 *             SQL异常
	 */
	public <T extends Serializable> int hardSave(T obj) throws SQLException {
		Script<List<Object>> sql = currentSQLDialect.get().hardSave(obj);
		return JDBCExecuteUtils.execute(CurrentConnectionHolder.get(), ExecuteUpdateSQLExecuter.getInstance(), null,
				sql.getValue(), sql.getParams(), showSql);
	}

	/**
	 * 硬保存。对所有字段执行插入/更新操作。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param rows
	 *            实体对象集
	 * @return 返回受影响行数
	 * @throws SQLException
	 *             SQL异常
	 */
	public <T extends Serializable> int hardSave(List<T> rows) throws SQLException {
		if (CollectionUtils.isEmpty(rows)) {
			return 0;
		}
		return JDBCExecuteUtils.hardSave(CurrentConnectionHolder.get(), currentSQLDialect.get(), rows, showSql);
	}

	/**
	 * 删除操作
	 * 
	 * @param obj
	 *            实体对象（不能为null）
	 * @return 返回受影响行数
	 * @throws SQLException
	 *             SQL异常
	 */
	public <T extends Serializable> int delete(T obj) throws SQLException {
		DML dml = DeleteDMLParser.getInstance().parse(obj.getClass());
		return JDBCExecuteUtils.execute(CurrentConnectionHolder.get(), ExecuteUpdateSQLExecuter.getInstance(), null,
				dml.getSql(), EntityUtils.getParams(obj, dml.getFields()), showSql);
	}

	/**
	 * 删除操作（实体对象集为空则直接返回0）
	 * 
	 * @param rows
	 *            实体对象集
	 * @return 返回受影响行数
	 * @throws SQLException
	 *             SQL异常
	 */
	public <T extends Serializable> int delete(List<T> rows) throws SQLException {
		return JDBCExecuteUtils.executeBatch(CurrentConnectionHolder.get(), DeleteDMLParser.getInstance(), rows,
				showSql);
	}

	/**
	 * 从数据库查询并组装实体对象。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param obj
	 *            实体对象
	 * @return 返回查询到的实体对象
	 * @throws SQLException
	 *             SQL异常
	 */
	@SuppressWarnings("unchecked")
	public <T extends Serializable> T get(T obj) throws SQLException {
		Class<T> type = (Class<T>) obj.getClass();
		DML dml = GetDMLParser.getInstance().parse(type);
		return JDBCExecuteUtils.execute(CurrentConnectionHolder.get(), new GetSQLExecuter<T>(type), null, dml.getSql(),
				EntityUtils.getParams(obj, dml.getFields()), showSql);
	}

	/**
	 * 使用动态结构化查询语言（DSQL）并组装对象，其中类型可以是实体对象，也可以是String、Number、
	 * Date、BigDecimal类型，这时将返回结果集中的第1行第1列的值。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param type
	 *            对象类型
	 * @param dsql
	 *            动态结构化查询语言
	 * @param params
	 *            查询参数键值集
	 * @return 返回查询到的对象
	 * @throws SQLException
	 *             SQL异常
	 */
	public <T extends Serializable> T get(Class<T> type, String dsql, Object... params) throws SQLException {
		return get(CurrentConnectionHolder.get(), DSQLFactory.parse(dsql, params), type);
	}

	/**
	 * 使用动态结构化查询语言（DSQL）并组装对象，其中类型可以是实体对象，也可以是String、Number、
	 * Date、BigDecimal类型，这时将返回结果集中的第1行第1列的值。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param type
	 *            对象类型
	 * @param dsql
	 *            动态结构化查询语言
	 * @param params
	 *            查询参数键值集
	 * @return 返回查询到的对象
	 * @throws SQLException
	 *             SQL异常
	 */
	public <T extends Serializable> T get(Class<T> type, String dsql, Map<String, ?> params) throws SQLException {
		return get(CurrentConnectionHolder.get(), DSQLFactory.parse(dsql, params), type);
	}

	/**
	 * 从数据库查询并组装实体对象列表。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param obj
	 *            实体对象
	 * @return 返回查询到的实体对象列表
	 * @throws SQLException
	 *             SQL异常
	 */
	@SuppressWarnings("unchecked")
	public <T extends Serializable> List<T> select(T obj) throws SQLException {
		Class<T> type = (Class<T>) obj.getClass();
		DML dml = GetDMLParser.getInstance().parse(type);
		return JDBCExecuteUtils.execute(CurrentConnectionHolder.get(), new SelectSQLExecuter<T>(type), null,
				dml.getSql(), EntityUtils.getParams(obj, dml.getFields()), showSql);
	}

	/**
	 * 使用动态结构化查询语言（DSQL）并组装对象列表，其中类型可以是实体对象，也可以是String、Number、
	 * Date、BigDecimal类型，这时将返回结果集中的第1列的值。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param type
	 *            对象类型
	 * @param dsql
	 *            动态结构化查询语言
	 * @param params
	 *            查询参数键值集
	 * @return 返回查询到的对象列表
	 * @throws SQLException
	 *             SQL异常
	 */
	public <T extends Serializable> List<T> select(Class<T> type, String dsql, Object... params) throws SQLException {
		return select(CurrentConnectionHolder.get(), DSQLFactory.parse(dsql, params), type);
	}

	/**
	 * 使用动态结构化查询语言（DSQL）并组装对象列表，其中类型可以是实体对象，也可以是String、Number、
	 * Date、BigDecimal类型，这时将返回结果集中的第1列的值。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param type
	 *            对象类型
	 * @param dsql
	 *            动态结构化查询语言
	 * @param params
	 *            查询参数键值集
	 * @return 返回查询到的对象列表
	 * @throws SQLException
	 *             SQL异常
	 */
	public <T extends Serializable> List<T> select(Class<T> type, String dsql, Map<String, ?> params)
			throws SQLException {
		return select(CurrentConnectionHolder.get(), DSQLFactory.parse(dsql, params), type);
	}

	/**
	 * 使用动态结构化查询语言（DSQL）执行插入、修改、删除操作。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param dsql
	 *            动态结构化查询语言
	 * @param params
	 *            查询参数键值集
	 * @return 如果第一个结果是ResultSet对象，则为true；如果第一个结果是更新计数或没有结果，则为false
	 * @throws SQLException
	 *             SQL异常
	 */
	public boolean execute(String dsql, Object... params) throws SQLException {
		return execute(DSQLFactory.parse(dsql, params));
	}

	/**
	 * 使用动态结构化查询语言（DSQL）执行插入、修改、删除操作。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param dsql
	 *            动态结构化查询语言
	 * @param params
	 *            查询参数键值集
	 * @return 如果第一个结果是ResultSet对象，则为true；如果第一个结果是更新计数或没有结果，则为false
	 * @throws SQLException
	 *             SQL异常
	 */
	public boolean execute(String dsql, Map<String, ?> params) throws SQLException {
		return execute(DSQLFactory.parse(dsql, params));
	}

	/**
	 * 使用动态结构化查询语言（DSQL）执行插入、修改、删除操作。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param dsql
	 *            动态结构化查询语言
	 * @param params
	 *            查询参数键值集
	 * @return 返回受影响行数
	 * @throws SQLException
	 *             SQL异常
	 */
	public int executeUpdate(String dsql, Object... params) throws SQLException {
		return executeUpdate(DSQLFactory.parse(dsql, params));
	}

	/**
	 * 使用动态结构化查询语言（DSQL）执行插入、修改、删除操作。该方法不自动提交事务，且调用前需要先调用beginTransaction方法开启事务，之后在合适的时机还需要调用commit方法提交事务。
	 * 
	 * @param dsql
	 *            动态结构化查询语言
	 * @param params
	 *            查询参数键值集
	 * @return 返回受影响行数
	 * @throws SQLException
	 *             SQL异常
	 */
	public int executeUpdate(String dsql, Map<String, ?> params) throws SQLException {
		return executeUpdate(DSQLFactory.parse(dsql, params));
	}

	/**
	 * 事务回滚。在业务方法发生异常时调用。
	 */
	public void rollback() {
		Connection con = getCurrentConnection();
		try {
			con.rollback();
		} catch (SQLException e) {
			throw new SQLExecutorException(e);
		} finally {
			JDBCUtils.close(con);
			CurrentConnectionHolder.remove();
			currentSQLDialect.remove();
		}
	}

	/**
	 * 提交事务
	 */
	public void commit() {
		Connection con = getCurrentConnection();
		try {
			con.commit();
		} catch (SQLException e) {
			throw new SQLExecutorException(e);
		} finally {
			JDBCUtils.close(con);
			CurrentConnectionHolder.remove();
		}
	}

	private <T extends Serializable> T get(Connection con, NamedSQL sql, Class<T> type) throws SQLException {
		return this.execute(con, sql, new GetSQLExecuter<T>(type));
	}

	private <T extends Serializable> List<T> select(Connection con, NamedSQL sql, Class<T> type) throws SQLException {
		return this.execute(con, sql, new SelectSQLExecuter<T>(type));
	}

	private boolean execute(NamedSQL namedSQL) throws SQLException {
		Script<List<Object>> sql = DSQLFactory.toJDBC(namedSQL);
		return (boolean) JDBCExecuteUtils.execute(getCurrentConnection(), ExecuteSQLExecuter.getInstance(),
				namedSQL.getId(), sql.getValue(), sql.getParams(), showSql);
	}

	private int executeUpdate(NamedSQL namedSQL) throws SQLException {
		Script<List<Object>> sql = DSQLFactory.toJDBC(namedSQL);
		return (int) JDBCExecuteUtils.execute(getCurrentConnection(), ExecuteUpdateSQLExecuter.getInstance(),
				namedSQL.getId(), sql.getValue(), sql.getParams(), showSql);
	}

	private <T> T execute(Connection con, NamedSQL namedSQL, SQLExecuter<T> sqlExecuter) throws SQLException {
		Script<List<Object>> sql = DSQLFactory.toJDBC(namedSQL);
		return JDBCExecuteUtils.execute(con, sqlExecuter, namedSQL.getId(), sql.getValue(), sql.getParams(), showSql);
	}

	private static Connection getCurrentConnection() {
		Connection con = CurrentConnectionHolder.get();
		if (con == null) {
			throw new IllegalCallException("You must call beginTransaction first before you call this method");
		}
		return con;
	}
}
