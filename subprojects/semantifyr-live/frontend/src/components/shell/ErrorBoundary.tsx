/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';

interface Props {
  /** Rendered in place of the children once a descendant throws during render. */
  fallback: React.ReactNode;
  /** Short label woven into the console error so the source boundary is identifiable. */
  label?: string;
  children: React.ReactNode;
}

interface State {
  hasError: boolean;
}

/**
 * Plain React error boundary. React only supports class components here, so this is the one
 * class component in the app. It does not attempt to recover (a re-mount would re-run the
 * same failing render); the fallback owns whatever recovery affordance makes sense, which in
 * practice is a full reload.
 */
export default class ErrorBoundary extends React.Component<Props, State> {
  override state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  override componentDidCatch(error: unknown): void {
    console.error(`semantifyr-live: ${this.props.label ?? 'render'} boundary caught`, error);
  }

  override render(): React.ReactNode {
    if (this.state.hasError) {
      return this.props.fallback;
    }
    return this.props.children;
  }
}
