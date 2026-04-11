/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import StatusBar from '../components/StatusBar';

describe('StatusBar', () => {
  it('renders the message text', () => {
    render(<StatusBar message="Ready" showProgress={false} />);
    expect(screen.getByText('Ready')).toBeInTheDocument();
  });

  it('renders a non-breaking space when message is null', () => {
    const { container } = render(<StatusBar message={null} showProgress={false} />);
    expect(container.textContent).toContain('\u00A0');
  });

  it('shows the progress bar when showProgress is true', () => {
    render(<StatusBar message="Loading..." showProgress={true} />);
    const progressBar = screen.getByRole('progressbar');
    expect(progressBar).toBeVisible();
  });

  it('hides the progress bar visually when showProgress is false', () => {
    const { container } = render(<StatusBar message="Ready" showProgress={false} />);
    const progressBar = container.querySelector('[role="progressbar"]');
    expect(progressBar).toBeInTheDocument();
    expect(progressBar).toHaveStyle({ visibility: 'hidden' });
  });

  it('renders info items when provided', () => {
    render(
      <StatusBar
        message="Ready"
        showProgress={false}
        infoItems={[
          { label: 'Language', value: 'oxsts' },
          { label: 'Backend', value: 'localhost:18080' },
        ]}
      />,
    );
    expect(screen.getByText('oxsts')).toBeInTheDocument();
    expect(screen.getByText('localhost:18080')).toBeInTheDocument();
  });
});
