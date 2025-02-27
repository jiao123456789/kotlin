/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.file.structure

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirModuleResolveComponents
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.collectDesignationWithFile
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.targets.LLFirClassWithSpecificMembersResolveTarget
import org.jetbrains.kotlin.analysis.low.level.api.fir.element.builder.getNonLocalContainingOrThisDeclaration
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.impl.FirPrimaryConstructor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.isContractDescriptionCallPsiCheck

internal object FileElementFactory {
    fun createFileStructureElement(
        firDeclaration: FirDeclaration,
        firFile: FirFile,
        moduleComponents: LLFirModuleResolveComponents,
    ): FileStructureElement = when {
        firDeclaration is FirRegularClass -> {
            lazyResolveClassWithGeneratedMembers(firDeclaration, moduleComponents)
            ClassDeclarationStructureElement(firFile, firDeclaration, moduleComponents)
        }

        firDeclaration is FirScript -> RootScriptStructureElement(firFile, firDeclaration, moduleComponents)
        else -> DeclarationStructureElement(firFile, firDeclaration, moduleComponents)
    }

    private fun lazyResolveClassWithGeneratedMembers(firClass: FirRegularClass, moduleComponents: LLFirModuleResolveComponents) {
        val classMembersToResolve = buildList {
            for (member in firClass.declarations) {
                when {
                    member is FirSimpleFunction && member.source?.kind == KtFakeSourceElementKind.DataClassGeneratedMembers -> {
                        add(member)
                    }

                    member is FirPrimaryConstructor && member.source?.kind == KtFakeSourceElementKind.ImplicitConstructor -> {
                        add(member)
                    }

                    member is FirProperty && member.source?.kind == KtFakeSourceElementKind.PropertyFromParameter -> {
                        add(member)
                    }

                    member is FirField && member.source?.kind == KtFakeSourceElementKind.ClassDelegationField -> {
                        add(member)
                    }

                    member is FirDanglingModifierList -> {
                        add(member)
                    }
                }
            }
        }

        val firClassDesignation = firClass.collectDesignationWithFile()
        val designationWithMembers = LLFirClassWithSpecificMembersResolveTarget(
            firClassDesignation.firFile,
            firClassDesignation.path,
            firClass,
            classMembersToResolve,
        )

        moduleComponents.firModuleLazyDeclarationResolver.lazyResolveTarget(
            designationWithMembers,
            FirResolvePhase.BODY_RESOLVE,
            towerDataContextCollector = null
        )
    }
}

/**
 * Covered by org.jetbrains.kotlin.analysis.low.level.api.fir.file.structure.AbstractInBlockModificationTest
 * on the compiler side and by
 * org.jetbrains.kotlin.idea.fir.analysis.providers.trackers.AbstractProjectWideOutOfBlockKotlinModificationTrackerTest
 * on the plugin part
 *
 * @return The declaration in which a change of the passed receiver parameter can be treated as in-block modification
 */
internal fun PsiElement.getNonLocalReanalyzableContainingDeclaration(): KtDeclaration? {
    return when (val declaration = getNonLocalContainingOrThisDeclaration()) {
        is KtNamedFunction -> declaration.takeIf { function ->
            function.isReanalyzableContainer() && isElementInsideBody(declaration = function, child = this)
        }

        is KtPropertyAccessor -> declaration.takeIf { accessor ->
            accessor.isReanalyzableContainer() && isElementInsideBody(declaration = accessor, child = this)
        }

        is KtProperty -> declaration.takeIf { property ->
            property.isReanalyzableContainer() && property.delegateExpressionOrInitializer?.isAncestor(this) == true
        }

        else -> null
    }
}

private fun isElementInsideBody(declaration: KtDeclarationWithBody, child: PsiElement): Boolean {
    val body = declaration.bodyExpression ?: return false
    if (!body.isAncestor(child)) return false
    return !isInsideContract(body = body, child = child)
}

private fun isInsideContract(body: KtExpression, child: PsiElement): Boolean {
    if (body !is KtBlockExpression) return false

    val firstStatement = body.firstStatement ?: return false
    if (!firstStatement.isContractDescriptionCallPsiCheck()) return false
    return firstStatement.isAncestor(child)
}

private fun KtNamedFunction.isReanalyzableContainer(): Boolean = hasBlockBody() || typeReference != null

private fun KtPropertyAccessor.isReanalyzableContainer(): Boolean = isSetter || hasBlockBody() || property.typeReference != null

private fun KtProperty.isReanalyzableContainer(): Boolean = typeReference != null && !hasDelegateExpressionOrInitializer()

