# SerialTweaker 
SerialTweaker: Interactive modification of Java Serialized Objects

<b>Overview</b>

SerialTweaker can be used to load a serialized object, change its contents, and reserialize it to a new serialized object with modified fields inside.

<b> WARNING!</b> This tool will deserialize input that it is given. It is therefore vulnerable to deserialization attacks by definition. Please make sure the input you use is not malicious, and/or usethe tool in an isolated sandboxed environment.
<pre>
-----------------
Serially - v1.1
by Stefan Broeder
-----------------
Usage:

SerialTweaker -b base64_encoded_java_object [OPTIONS]
SerialTweaker -v url_to_get_viewstate_from [OPTIONS]

OPTIONS:
-k      DES key to decrypt the object. Format: Base64
-d      Maximum depth (to prevent from printing deeply nested objects). Default: 3. To disable, set 0.
</pre>
For more information about how to use the tool, please see [this blog post](https://www.redtimmy.com/web-application-hacking/interactive-modification-of-java-serialized-objects-with-serialtweaker).

<b>Dependencies</b>
A local repository of jar files is required in ~/.serially/jars. It can be built and indexed with the JavaClassDB.py tool from the [EnumJavaLibs](https://github.com/redtimmy/EnumJavaLibs) project.
