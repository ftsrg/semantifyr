/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

private val NON_FS_SAFE = Regex("[^A-Za-z0-9._-]")

private const val MAX_CASE_DIRECTORY_LENGTH = 64

// the model file tells apart cases whose qualified names repeat across models, and the bound keeps backend working directories under Windows' 260 characters
fun caseDirectoryName(case: VerificationCase): String {
    val model = case.classDeclaration.eResource()?.uri?.trimFileExtension()?.lastSegment()
    val name = listOfNotNull(model, case.qualifiedName.substringAfterLast("::")).joinToString(".").replace(NON_FS_SAFE, "_")
    if (name.length <= MAX_CASE_DIRECTORY_LENGTH) {
        return name
    }
    val hash = "%08x".format(name.hashCode())
    return "${name.take(MAX_CASE_DIRECTORY_LENGTH - hash.length - 1)}-$hash"
}
