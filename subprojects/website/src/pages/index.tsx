/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Layout from '@theme/Layout';
import ThemedImage from '@theme/ThemedImage';
import clsx from 'clsx';

function OpenInNewIcon(): React.ReactNode {
  return (
    <svg viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor" style={{ verticalAlign: 'middle', marginLeft: '0.3em' }}>
      <path d="M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z" />
    </svg>
  );
}

function ArrowForwardIcon(): React.ReactNode {
  return (
    <svg viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor" style={{ verticalAlign: 'middle', marginLeft: '0.3em' }}>
      <path d="m12 4-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z" />
    </svg>
  );
}

import styles from './index.module.css';

const LIVE_URL = 'https://live.semantifyr.org';
const HUB_URL = 'https://hub.semantifyr.org';

type HealthStatus = 'checking' | 'online' | 'offline';

function useHealthCheck(healthUrl: string): HealthStatus {
  const [status, setStatus] = React.useState<HealthStatus>('checking');

  React.useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 5000);
    fetch(healthUrl, { signal: controller.signal, mode: 'no-cors' })
      .then(() => { if (!cancelled) setStatus('online'); })
      .catch(() => { if (!cancelled) setStatus('offline'); })
      .finally(() => clearTimeout(timer));
    return () => { cancelled = true; controller.abort(); };
  }, [healthUrl]);

  return status;
}

function HealthDot({ status }: { status: HealthStatus }): React.ReactNode {
  if (status === 'checking') return null;
  const cls = status === 'online' ? styles['health-dot--online'] : styles['health-dot--offline'];
  const label = status === 'online' ? 'Service is online' : 'Service appears offline';
  return <span className={clsx(styles['health-dot'], cls)} title={label} aria-label={label} />;
}

function Hero() {
  const lightLogo = useBaseUrl('/img/logo-full-light.svg');
  const darkLogo = useBaseUrl('/img/logo-full-dark.svg');
  return (
    <header className={styles['hero']}>
      <div className={clsx('container', styles['hero__inner'])}>
        <h1 className={styles['hero__title']}>
          <span className={styles['hero__sr']}>Semantifyr</span>
          <ThemedImage
            className={styles['hero__logo']}
            alt="Semantifyr"
            sources={{ light: lightLogo, dark: darkLogo }}
          />
        </h1>
        <p className={styles['hero__tagline']}>
          A framework to support the declarative definition of engineering language semantics.
        </p>
        <div className={styles['hero__buttons']}>
          <Link
            href={LIVE_URL}
            className={clsx(
              'button',
              'button--lg',
              'button--primary',
              styles['hero__button'],
            )}
          >
            Try it live <OpenInNewIcon />
          </Link>
          <Link
            to="/learn"
            className={clsx(
              'button',
              'button--lg',
              'button--outline',
              'button--primary',
              styles['hero__button'],
            )}
          >
            Learn the language <ArrowForwardIcon />
          </Link>
        </div>
      </div>
    </header>
  );
}

function TryIt() {
  const liveHealth = useHealthCheck(`${LIVE_URL}/api/health`);
  const hubHealth = useHealthCheck(`${HUB_URL}/hub/health`);

  return (
    <section className={styles['section']}>
      <div className="container">
        <h2 className={styles['section__title']}>Try it online</h2>
        <div className={styles['cards']}>
          <div className={styles['card']}>
            <div className={styles['card__header']}>
              <h3 className={styles['card__title']}>In-browser editor</h3>
              <HealthDot status={liveHealth} />
            </div>
            <p className={styles['card__body']}>
              A lightweight editor for quick experimentation. Supports syntax
              highlighting, diagnostics, content assist, and an integrated
              verification panel. No installation or account required.
            </p>
            <Link
              href={LIVE_URL}
              className={clsx('button', 'button--primary', 'button--block', styles['card__button'])}
            >
              Open the live editor <OpenInNewIcon />
            </Link>
          </div>
          <div className={styles['card']}>
            <div className={styles['card__header']}>
              <h3 className={styles['card__title']}>JupyterHub environment</h3>
              <HealthDot status={hubHealth} />
            </div>
            <p className={styles['card__body']}>
              A full-fledged VS Code environment with the Semantifyr extension,
              multi-file project support, and integration with SysML v2 and the
              Theta model checker backend.
            </p>
            <p className={styles['card__note']}>
              Access requires a user account.{' '}
              <Link href="https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTORS.md">
                Contact the maintainers
              </Link>{' '}
              if you don&apos;t have one yet.
            </p>
            <Link
              href="https://hub.semantifyr.org"
              className={clsx('button', 'button--primary', 'button--block', styles['card__button'])}
            >
              Launch JupyterHub <OpenInNewIcon />
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}

function LearnMore() {
  return (
    <section className={clsx(styles['section'], styles['section--alt'])}>
      <div className="container">
        <h2 className={styles['section__title']}>Get started</h2>
        <div className={clsx('row', styles['features-row'])}>
          <div className="col col--4">
            <div className={styles['feature']}>
              <h3>Tutorials</h3>
              <p>
                Build a traffic light model from scratch, discover and fix a
                safety bug, then refactor it into a reusable library.
              </p>
              <Link to="/learn/tutorials">Start the tutorials <ArrowForwardIcon /></Link>
            </div>
          </div>
          <div className="col col--4">
            <div className={styles['feature']}>
              <h3>Language Reference</h3>
              <p>
                Quick-lookup reference for every Semantifyr construct: classes,
                features, transitions, properties, expressions, and more.
              </p>
              <Link to="/learn/language">Browse the reference <ArrowForwardIcon /></Link>
            </div>
          </div>
          <div className="col col--4">
            <div className={styles['feature']}>
              <h3>Contribute</h3>
              <p>
                Semantifyr is open source. Fork the repository, build from
                source, and start contributing to the compiler, frontends, or
                tooling.
              </p>
              <Link to="/develop">Developer guide <ArrowForwardIcon /></Link>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

export default function Home() {
  return (
    <Layout>
      <div className={styles['page']}>
        <Hero />
        <TryIt />
        <LearnMore />
      </div>
    </Layout>
  );
}
