package com.phoenixnap.oss.ramlapisync.pojo;

import com.phoenixnap.oss.ramlapisync.generation.CodeModelHelper;
import com.phoenixnap.oss.ramlapisync.raml.RamlRoot;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import org.raml.v2.api.model.v10.datamodel.AnyTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Interpreter for Any types.
 *
 * @author Aleksandar Stojsavljevic
 * @since 0.10.2
 *
 */
public class AnyTypeInterpreter extends BaseTypeInterpreter {

	private Set<Class<? extends TypeDeclaration>> set;

	@Override
	public Set<Class<? extends TypeDeclaration>> getSupportedTypes() {
		if (set == null) {
			set = new LinkedHashSet<>(1);
			set.add(AnyTypeDeclaration.class);
		}
		return set;
	}

	@Override
	public RamlInterpretationResult interpret(RamlRoot document, TypeDeclaration type, JCodeModel builderModel,
                                              PojoGenerationConfig config, boolean property, String customName) {

		AnyTypeDeclaration anyTypeDeclaration = (AnyTypeDeclaration) type;

		RamlInterpretationResult result = new RamlInterpretationResult(type.required());
		String objectName = Map.class.getName();

		JClass resolvedClass = CodeModelHelper.findFirstClassBySimpleName(builderModel, objectName);
		JClass stringRefClass = builderModel.ref(String.class);
		result.setResolvedClass(resolvedClass.narrow(stringRefClass, stringRefClass));
		return result;
	}
}
