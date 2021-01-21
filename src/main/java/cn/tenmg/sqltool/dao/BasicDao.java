package cn.tenmg.sqltool.dao;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.sql.DataSource;

import cn.tenmg.sqltool.DSQLFactory;
import cn.tenmg.sqltool.datasource.DataSourceFactory;
import cn.tenmg.sqltool.exception.IllegalConfigException;
import cn.tenmg.sqltool.exception.InitializeDataSourceException;
import cn.tenmg.sqltool.factory.XMLFileDSQLFactory;
import cn.tenmg.sqltool.utils.CollectionUtils;
import cn.tenmg.sqltool.utils.SQLDialectUtils;

public class BasicDao extends AbstractDao {

	private static final String DATASOURCE_PREFIX = "sqltool.datasource.", DEFAULT_NAME = "default",
			DATASOURCE_REGEX = "^".concat(DATASOURCE_PREFIX.replaceAll("\\.", "\\\\."))
					.concat("([\\S]+\\.){0,1}[^\\.]+$");

	private static final int DATASOURCE_PREFIX_LEN = DATASOURCE_PREFIX.length();

	private final Map<String, DataSource> dataSources = new HashMap<String, DataSource>();

	private DataSource defaultDataSource = null;

	private DSQLFactory dsqlFactory;

	private boolean showSql;

	private int defaultBatchSize = 500;

	private BasicDao(Properties properties) {
		super();
		String basePackages = properties.getProperty("sqltool.basePackages"),
				suffix = properties.getProperty("sqltool.suffix");
		if (suffix == null) {
			this.dsqlFactory = new XMLFileDSQLFactory(basePackages);
		} else {
			this.dsqlFactory = new XMLFileDSQLFactory(basePackages, suffix);
		}
		this.showSql = Boolean.valueOf(properties.getProperty("sqltool.showSql", "false"));
		this.defaultBatchSize = Integer.valueOf(properties.getProperty("sqltool.defaultBatchSize", "500"));
		Map<String, Properties> datasourceConfigs = new HashMap<String, Properties>();
		String key, name, param, firstName = null;
		Object value;
		Properties datasourceConfig;
		for (Iterator<Entry<Object, Object>> it = properties.entrySet().iterator(); it.hasNext();) {
			Entry<Object, Object> entry = it.next();
			key = entry.getKey().toString();
			value = entry.getValue();
			if (key.matches(DATASOURCE_REGEX)) {
				param = key.substring(DATASOURCE_PREFIX_LEN);
				int index = param.indexOf(".");
				if (index > 0) {
					name = param.substring(0, index);
					param = param.substring(index);
				} else {
					name = DEFAULT_NAME;
				}
				if (firstName == null) {
					firstName = name;
				}
				datasourceConfig = datasourceConfigs.get(name);
				if (datasourceConfig == null) {
					datasourceConfig = new Properties();
					datasourceConfigs.put(name, datasourceConfig);
				}
				datasourceConfig.put(param, value);
			}
		}
		if (CollectionUtils.isEmpty(datasourceConfigs)) {
			throw new IllegalConfigException("No datasource is configured, please check the configuration");
		}
		String defaultName = DEFAULT_NAME;
		datasourceConfig = datasourceConfigs.get(DEFAULT_NAME);
		if (datasourceConfig == null) {// 默认数据源不存在则将第一个数据源作为默认数据源
			defaultName = firstName;
			datasourceConfig = datasourceConfigs.get(firstName);
		}
		try {
			defaultDataSource = DataSourceFactory.createDataSource(datasourceConfig);
			dataSources.put(defaultName, defaultDataSource);
			cacheSQLDialect(defaultDataSource, SQLDialectUtils.getSQLDialect(datasourceConfig));
			datasourceConfigs.remove(defaultName);
			DataSource dataSource;
			for (Iterator<Entry<String, Properties>> it = datasourceConfigs.entrySet().iterator(); it.hasNext();) {
				Entry<String, Properties> entry = it.next();
				datasourceConfig = entry.getValue();
				dataSource = DataSourceFactory.createDataSource(datasourceConfig);
				dataSources.put(entry.getKey(), dataSource);
				cacheSQLDialect(dataSource, SQLDialectUtils.getSQLDialect(datasourceConfig));
			}
		} catch (Exception e) {
			throw new InitializeDataSourceException("An exception occurred while initializing datasource(s)", e);
		}
	}

	public static BasicDao build(Properties properties) {
		return new BasicDao(properties);
	}

	@Override
	public DSQLFactory getDSQLFactory() {
		return dsqlFactory;
	}

	@Override
	public DataSource getDefaultDataSource() {
		return defaultDataSource;
	}

	@Override
	public DataSource getDataSource(String name) {
		return dataSources.get(name);
	}

	@Override
	boolean isShowSql() {
		return showSql;
	}

	@Override
	int getDefaultBatchSize() {
		return defaultBatchSize;
	}

}
