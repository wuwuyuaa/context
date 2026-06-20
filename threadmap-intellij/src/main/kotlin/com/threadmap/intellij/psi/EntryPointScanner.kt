package com.threadmap.intellij.psi

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

/** 项目里一个 HTTP 入口:动词 + 路径 + 所在控制器方法。 */
data class EntryPoint(
    val verb: String,
    val path: String,
    val className: String,
    val methodName: String,
    val method: PsiMethod,
) {
    val label: String get() = "$verb $path"
    val signature: String get() = "$className#$methodName"
}

/** 扫项目里 @RestController / @Controller 上带 mapping 注解的方法,作为"入口清单"的正门。 */
object EntryPointScanner {
    private const val REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping"
    private val MAPPINGS = listOf(
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.PutMapping",
        "org.springframework.web.bind.annotation.DeleteMapping",
        "org.springframework.web.bind.annotation.PatchMapping",
        REQUEST_MAPPING,
    )
    private val CONTROLLERS = listOf(
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.stereotype.Controller",
    )

    /** 必须在 ReadAction 内调用(访问 PSI / 索引搜索)。 */
    fun scan(project: Project): List<EntryPoint> {
        val scope = GlobalSearchScope.projectScope(project)
        val facade = JavaPsiFacade.getInstance(project)
        val out = LinkedHashMap<String, EntryPoint>()
        for (ctrlFqn in CONTROLLERS) {
            val anno = facade.findClass(ctrlFqn, scope) ?: continue
            for (cls in AnnotatedElementsSearch.searchPsiClasses(anno, scope).findAll()) {
                val basePath = classPath(cls)
                for (m in cls.methods) {
                    val mapping = methodMapping(m) ?: continue
                    val ep = EntryPoint(
                        verb = mapping.first,
                        path = EndpointLabel.path(basePath, mapping.second),
                        className = cls.name ?: "?",
                        methodName = m.name,
                        method = m,
                    )
                    out.putIfAbsent("${ep.verb} ${ep.path} ${ep.signature}", ep)
                }
            }
        }
        return out.values.sortedWith(compareBy({ it.path }, { it.verb }))
    }

    private fun methodMapping(m: PsiMethod): Pair<String, String?>? {
        for (fqn in MAPPINGS) {
            val anno = AnnotationUtil.findAnnotation(m, fqn) ?: continue
            val short = fqn.substringAfterLast('.')
            val path = AnnotationUtil.getStringAttributeValue(anno, "value")
                ?: AnnotationUtil.getStringAttributeValue(anno, "path")
            return EndpointLabel.verb(short) to path
        }
        return null
    }

    private fun classPath(cls: PsiClass): String? {
        val anno = AnnotationUtil.findAnnotation(cls, REQUEST_MAPPING) ?: return null
        return AnnotationUtil.getStringAttributeValue(anno, "value")
            ?: AnnotationUtil.getStringAttributeValue(anno, "path")
    }
}
