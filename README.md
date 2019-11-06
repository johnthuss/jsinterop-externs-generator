***This is a work in progress and not currently intended for production use.***

This is a java annotation processor to generate closure js externs definitions from
jsinterop-annotated classes/interfaces that use `@JsType(isNative=true)`. These externs are 
necessary for closure to compile these classes after j2cl has converted them to js.

This project has many limitations currently, including the fact that it will only generate
definitions for getter methods.

## Example Output
Given a jsinterop annotated class like:

	import jsinterop.annotations.JsOverlay;
	import jsinterop.annotations.JsPackage;
	import jsinterop.annotations.JsProperty;
	import jsinterop.annotations.JsType;
	import jsinterop.base.Js;
	import jsinterop.base.JsPropertyMap;
	
	@JsType(isNative = true, namespace = JsPackage.GLOBAL)
	public interface AuthRequest {
		@JsOverlay
		static AuthRequest create() {
			return Js.uncheckedCast(JsPropertyMap.of());
		}
	
		@JsProperty String getUsername();
		@JsProperty void setUsername(String value);
	
		@JsProperty String getPassword();
		@JsProperty void setPassword(String value);
	}

This will generate the file AuthRequest-externs.js with the content:

	/** 
	 * @externs 
	 */ 
	
	/** 
	 * @constructor 
	 */ 
	function AuthRequest() {}
	/** @const {string} */
	AuthRequest.prototype.username;
	/** @const {string} */
	AuthRequest.prototype.password;

## Usage
This library can be used from most build tools (maven, ant, etc) automatically just by adding it to the compiler's classpath.

 
To use this annotation processor with bazel for j2cl you need to add it as a plugin to 
your j2cl_library task in your BUILD file:

	j2cl_library(
	    ...
	    plugins = ["@jsinterop_externs_generator//:jsinterop-externs-generator-plugin"],
	)


You'll need to reference the repo for this project in the WORKSPACE file. One way to do it,
if you have this repo cloned locally, is:

	local_repository(
	    name = "jsinterop_externs_generator",
	    path = "../jsinterop-externs-generator",
	)
