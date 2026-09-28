import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;
public final class JavaSyntaxCheck {
 public static void main(String[] args)throws Exception{
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
  try(StandardJavaFileManager fm=compiler.getStandardFileManager(diagnostics,null,java.nio.charset.StandardCharsets.UTF_8)){
   var paths=Files.walk(Path.of("app/src/main/java")).filter(p->p.toString().endsWith(".java")).toList();
   JavacTask task=(JavacTask)compiler.getTask(null,fm,diagnostics,List.of("-proc:none","--release","17"),null,fm.getJavaFileObjectsFromPaths(paths));
   task.parse();
   for(var d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new AssertionError(d.toString());
   System.out.println("Java syntax: "+paths.size()+" source files parsed; syntax stage only; Android type checking is performed by Gradle.");
  }
 }
}
