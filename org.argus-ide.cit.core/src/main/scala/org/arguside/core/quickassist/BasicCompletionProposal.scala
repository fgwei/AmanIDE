package org.arguside.core.quickassist

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point

/**
 * Models an entry in the quick assist proposal.
 *
 * @param relevance
 *        Denotes at which position in among all available entries this entry
 *        occurs. A higher value means a better position.
 * @param displayString
 *        A text of explanation shown in the completion proposal.
 * @param image
 *        An image shown beside the `displayString`. If this is `null` no image
 *        is shown.
 */
abstract class BasicCompletionProposal(relevance: Int, displayString: String, image: Image = null) extends IJavaCompletionProposal {
  override def getRelevance(): Int = relevance
  override def getDisplayString(): String = displayString
  override def getSelection(document: IDocument): Point = null
  override def getAdditionalProposalInfo(): String = null
  override def getImage(): Image = image
  override def getContextInformation(): IContextInformation = null
}
