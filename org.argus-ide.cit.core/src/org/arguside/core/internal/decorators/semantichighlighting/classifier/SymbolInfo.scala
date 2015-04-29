package org.arguside.core.internal.decorators.semantichighlighting.classifier

import org.eclipse.jface.text.IRegion

case class SymbolInfo(symbolType: SymbolTypes.SymbolType, regions: List[IRegion], deprecated: Boolean, inInterpolatedString: Boolean)


object SymbolTypes extends Enumeration {
  type SymbolType = Value

  val Annotation, CaseClass, CaseObject, Class , LazyLocalVal,
      LazyTemplateVal , LocalVar, LocalVal, Method, Param, Object,
      Package, TemplateVar, TemplateVal, Trait, Type, TypeParameter,
      DynamicSelect, DynamicUpdate, DynamicApply, DynamicApplyNamed,
      CallByNameParameter = Value
}
