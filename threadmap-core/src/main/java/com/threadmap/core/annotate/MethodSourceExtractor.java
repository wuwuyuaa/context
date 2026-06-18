package com.threadmap.core.annotate;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 用 JavaParser 按 trace 签名(类#方法(简单参数类型))在源码根里定位方法,
 * 抽取方法源码 + 直接被调方法名。按 方法名 + 参数个数 匹配(重载时取首个,已知局限)。
 */
public class MethodSourceExtractor {
    private final List<Path> sourceRoots;

    public MethodSourceExtractor(List<Path> sourceRoots) {
        this.sourceRoots = List.copyOf(sourceRoots);
    }

    public Optional<Extracted> extract(String signature) {
        Parsed sig = parseSignature(signature);
        if (sig == null) {
            return Optional.empty();
        }
        Path file = locate(sig.fqcn());
        if (file == null) {
            return Optional.empty();
        }
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(Files.readString(file));
        } catch (Exception e) {
            return Optional.empty();
        }
        // 先 scope 到目标(可能是嵌套)类型,再在其直接方法里按 名字+参数个数 匹配,
        // 避免把 Outer$Inner#m() 串到外层 Outer 的同名方法。
        return cu.findAll(TypeDeclaration.class).stream()
                .map(t -> (TypeDeclaration<?>) t)
                .filter(t -> t.getNameAsString().equals(sig.simpleType()))
                .flatMap(t -> t.getMethods().stream())
                .filter(m -> m.getNameAsString().equals(sig.method()))
                .filter(m -> m.getParameters().size() == sig.arity())
                .findFirst()
                .map(MethodSourceExtractor::toExtracted);
    }

    private static Extracted toExtracted(MethodDeclaration method) {
        Set<String> callees = new LinkedHashSet<>();
        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            callees.add(call.getNameAsString());
        }
        return new Extracted(method.toString(), new ArrayList<>(callees));
    }

    /** FQCN(取 # 前;内部类 $ 截到外层)→ 在各 source root 下找 a/b/C.java。 */
    private Path locate(String fqcn) {
        int dollar = fqcn.indexOf('$');
        String outer = dollar >= 0 ? fqcn.substring(0, dollar) : fqcn;
        String relative = outer.replace('.', '/') + ".java";
        for (Path root : sourceRoots) {
            Path candidate = root.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Parsed parseSignature(String signature) {
        int hash = signature.indexOf('#');
        if (hash < 0) {
            return null;
        }
        int open = signature.indexOf('(', hash + 1);
        int close = signature.lastIndexOf(')');
        if (open < hash || close < open) {
            return null;
        }
        String fqcn = signature.substring(0, hash);
        String method = signature.substring(hash + 1, open);
        String params = signature.substring(open + 1, close).trim();
        int arity = params.isEmpty() ? 0 : params.split(",").length;
        return new Parsed(fqcn, simpleTypeOf(fqcn), method, arity);
    }

    /** FQCN 的最内层简单类名:com.example.Outer$Inner → Inner;com.example.Sample → Sample。 */
    private static String simpleTypeOf(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        String afterPackage = lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
        int lastDollar = afterPackage.lastIndexOf('$');
        return lastDollar >= 0 ? afterPackage.substring(lastDollar + 1) : afterPackage;
    }

    private record Parsed(String fqcn, String simpleType, String method, int arity) {
    }

    /** 抽取结果:方法源码 + 直接被调方法名(去重,保序)。 */
    public record Extracted(String source, List<String> calleeNames) {
        public Extracted {
            calleeNames = List.copyOf(calleeNames);
        }
    }
}
