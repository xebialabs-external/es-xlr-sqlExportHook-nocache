package ext.deployit.releasehandler;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import com.mchange.v2.c3p0.ComboPooledDataSource;

public class DataSource {
	private static DataSource datasource;
	private ComboPooledDataSource ds;

	private DataSource(String driver, String jdbcUrl, String username, String password)
			throws IOException, SQLException, PropertyVetoException {
		ds = new ComboPooledDataSource();
		ds.setDriverClass(driver);
		ds.setUser(username);
		ds.setPassword(password);
		ds.setJdbcUrl(jdbcUrl);

		// the settings below are optional -- dbcp can work with defaults
		ds.setInitialPoolSize(5);
        ds.setMinPoolSize(5);
        ds.setAcquireIncrement(5);
        ds.setMaxPoolSize(50);
        
	}

	public static DataSource getInstance(String driver, String jdbcUrl, String username, String password)
			throws IOException, SQLException, PropertyVetoException {
		if (datasource == null) {
			datasource = new DataSource(driver, jdbcUrl, username, password);
			return datasource;
		} else {
			return datasource;
		}
	}

	public Connection getConnection() throws SQLException {
		Connection con = this.ds.getConnection();
		con.setAutoCommit(false);
		return con;
	}

}
