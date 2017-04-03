package ext.deployit.releasehandler;

import java.util.List;

import com.xebialabs.xlrelease.domain.variables.Variable;

public class ReleaseExportHelper {

	public static Variable getVariableByName(List<Variable> variables, String varibaleName) {
		for (Variable variable : variables) {
			if (variable.getKey().equalsIgnoreCase(varibaleName)) {
				return variable;
			}
		}
		return null;
	}
}
