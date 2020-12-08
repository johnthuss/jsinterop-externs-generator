package com.icsusa.jsinterop;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes("jsinterop.annotations.JsType")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class ExternsProcessor extends AbstractProcessor {

	@Override
	public boolean process(Set<? extends TypeElement> annotations, 
			RoundEnvironment roundEnv) {
		
	    for (TypeElement annotation : annotations) {
	    	Element nativeParam = annotation.getEnclosedElements().stream().filter(e -> {
	    		return "isNative".equals(e.getSimpleName().toString());
	    	}).findFirst().get();
	    	
	    	Element nameParam = annotation.getEnclosedElements().stream().filter(e -> {
	    		return "name".equals(e.getSimpleName().toString());
	    	}).findFirst().get();
	    	
	        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
	        for (Element element : annotatedElements) {
	        	Optional<? extends AnnotationMirror> nativeJsTypeAnnotation = 
	        			findNativeJsTypeAnnotation(nativeParam, element);
	        	if (!nativeJsTypeAnnotation.isPresent()) continue;
	        	
//	        	System.out.println("Generating externs for " + element.getSimpleName());
	        	
				List<String[]> props = processingEnv.getElementUtils().getAllMembers((TypeElement)element).stream() // TODO: limit to members declared in this class/interface, not inherited
					.filter(el -> el.getKind() == ElementKind.METHOD)
					.filter(el -> el.getSimpleName().toString().startsWith("get") && // TODO: support methods other than getters?
							!el.getSimpleName().toString().equals("getClass"))
					.map(el -> {
						String name = el.getSimpleName().toString();
						name = lowerFirstLetter(name.replaceFirst("^get([A-Z])", "$1"));
						
						ExecutableType t = (ExecutableType)el.asType();
						String returnType = getClosureType(t, nativeParam, nameParam);
						
						return new String[] { name, returnType };
					}).collect(Collectors.toList());
				
				if (props.isEmpty()) continue;
				
				Filer filer = processingEnv.getFiler();
				try {
					PackageElement pack = processingEnv.getElementUtils().getPackageOf(element);
					FileObject resource = filer.createResource(StandardLocation.SOURCE_OUTPUT, 
						pack.getQualifiedName().toString(), 
						((TypeElement)element).getSimpleName() + "-externs.js", 
						(Element[])null);
					
					TypeMirror superclass = ((TypeElement)element).getSuperclass();
					boolean hasSuperclass = !(superclass.getKind().equals(TypeKind.NONE));
					if (!hasSuperclass && !((TypeElement)element).getInterfaces().isEmpty()) {
						superclass = ((TypeElement)element).getInterfaces().get(0); // TODO: support multiple interfaces?
						hasSuperclass = !(superclass.getKind().equals(TypeKind.NONE));
					}
					try (PrintWriter out = new PrintWriter(resource.openWriter())) {
						out.println("/** ");
						out.println(" * @externs ");
						out.println(" */ ");
						out.println();
						
						out.println("/** ");
						out.println(" * @constructor ");
						if (hasSuperclass) {
							out.println(" * @extends {" + ((DeclaredType)superclass).asElement().getSimpleName() + "} ");
						}
						out.println(" */ ");
						out.println("function " + ((TypeElement)element).getSimpleName() + "() {}");
						
						for (String[] prop : props) {
							out.println("/** @const {" + prop[1] + "} */");
							out.println(((TypeElement)element).getSimpleName() + ".prototype." + prop[0] + ";");
						}
						
						out.println();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
	    }
	    
		return true;
	}

	public static Optional<? extends AnnotationMirror> findNativeJsTypeAnnotation(Element nativeParam, Element element) {
		Optional<? extends AnnotationMirror> nativeJsTypeAnnotation = element.getAnnotationMirrors().stream()
				.filter(a -> {
					return "JsType".equals(a.getAnnotationType().asElement().getSimpleName().toString());
				})
				.filter(a -> {
					AnnotationValue nativeValue = a.getElementValues().get(nativeParam);
					return nativeValue != null && Boolean.TRUE.equals(nativeValue.getValue());
				})
				.findAny();
		return nativeJsTypeAnnotation;
	}
	
	/**
	 * Converts a java type name to an equivalent Closure/JS type name
	 * @param t the java type representation to convert
	 * @param nativeParam the JsType.isNative parameter representation
	 * @param nameParam the JsType.name parameter representation
	 */
	static String getClosureType(ExecutableType t, Element nativeParam, Element nameParam) {
		String javaType;
		if (t.getReturnType() instanceof PrimitiveType) {
			PrimitiveType pt = (PrimitiveType)t.getReturnType();
			javaType = pt.getKind().name().toLowerCase();
		} else if (t.getReturnType() instanceof ArrayType) {
			javaType = "java.util.List";
		} else /*if (t.getReturnType() instanceof DeclaredType)*/ {
			DeclaredType dt = (DeclaredType)t.getReturnType();
			TypeElement te = (TypeElement)dt.asElement();
        	Optional<? extends AnnotationMirror> nativeAnnotation = 
        			findNativeJsTypeAnnotation(nativeParam, te);
        	if (nativeAnnotation.isPresent()) {
        		if (nativeAnnotation.get().getElementValues().get(nameParam) != null) {
        			return nativeAnnotation.get().getElementValues().get(nameParam).getValue().toString();
        		}
        		return te.getSimpleName().toString(); // skip conversion to closure type for native JsTypes
        	}
        	javaType = te.getQualifiedName().toString();
		}
		
		switch (javaType) {
		case "java.lang.Integer":
		case "int":
		case "java.lang.Short":
		case "short":
		case "java.lang.Long":
		case "long":
		case "java.lang.Double":
		case "double":
		case "java.lang.Float":
		case "float":
		case "java.math.BigDecimal":
			return "number";
		case "java.lang.Boolean":
		case "boolean":
			return "boolean";
		case "java.util.List":
			return "Array";
		case "java.util.Date":
		case "java.sql.Timestamp":
		case "java.time.LocalDate":
		case "java.time.DateTime":
		case "java.time.LocalTime":
		case "java.time.LocalDateTime":
		case "org.joda.time.LocalDate":
		case "org.joda.time.DateTime":
		case "org.joda.time.LocalTime":
		case "org.joda.time.LocalDateTime":
			return "Date";
		case "java.lang.String":
			return "string";
		default:
			return "Object";
		}
	}

	private static String lowerFirstLetter(String str) {
		if (str == null) return null;
		if (str.isEmpty()) return "";
		return str.substring(0, 1).toLowerCase() + str.substring(1);
	}
	
}