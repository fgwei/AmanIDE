package argus.tools.eclipse.contribution.weaving.jdt;

import org.eclipse.jface.resource.ImageDescriptor;

public interface IPilarElement {
	public ImageDescriptor getImageDescriptor();

	public String getLabelText(long flags);
}
