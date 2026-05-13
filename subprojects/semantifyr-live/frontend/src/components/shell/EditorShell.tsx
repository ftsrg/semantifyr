/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react'
import Box from '@mui/material/Box'

import Toolbar from './Toolbar'
import StatusBar, { type StatusBarInfoItem } from './StatusBar'
import DevInfoPanel from '../dev/DevInfoPanel'
import ActiveVerificationsMonitor from '../verification/ActiveVerificationsMonitor'
import PortfolioSettings from '../verification/PortfolioSettings'
import type { LiveEditorHandle, LiveEditorStatus } from '../editor/LiveEditor'
import type { ColorModePreference } from '../../lib/util/colorMode'
import type { LiveFlavor } from '../../examples'
import type { FlavorInfo, PortfolioInfo } from '../../lib/api'

interface Props {
  logoSrc: string
  flavors: readonly LiveFlavor[]
  currentFlavorId: string
  currentExampleId: string
  onSelectModel: (flavorId: string, exampleId: string) => void
  onCopyLink: () => void
  copyConfirmation: string | null
  connectionStatus: LiveEditorStatus
  onReconnect: () => void
  onDisconnect: () => void
  statusBarMessage: string | null
  statusBarShowProgress: boolean
  statusBarInfoItems: readonly StatusBarInfoItem[]
  onStatusInfoClick: () => void
  onOpenVerificationsTab: () => void
  portfolios: readonly PortfolioInfo[]
  portfolioId: string
  onPortfolioChange: (id: string) => void
  validationPortfolioId: string
  onValidationPortfolioChange: (id: string) => void
  flavorInfo: FlavorInfo | null
  colorModePreference: ColorModePreference
  onToggleColorMode: () => void
  devPanelOpen: boolean
  onCloseDevPanel: () => void
  language: string
  connectedSince: number | null
  reconnectCount: number
  editorHandle: LiveEditorHandle | null
  backendUrl: string
  children: React.ReactNode
}

export default function EditorShell(props: Props): React.JSX.Element {
  return (
    <>
      <Toolbar
        logoSrc={props.logoSrc}
        flavors={props.flavors}
        currentFlavorId={props.currentFlavorId}
        currentExampleId={props.currentExampleId}
        onSelectModel={props.onSelectModel}
        connectionStatus={props.connectionStatus}
        onReconnect={props.onReconnect}
        onDisconnect={props.onDisconnect}
        onCopyLink={props.onCopyLink}
        copyConfirmation={props.copyConfirmation}
        colorModePreference={props.colorModePreference}
        onToggleColorMode={props.onToggleColorMode}
      />
      {props.children}
      <StatusBar
        message={props.statusBarMessage}
        showProgress={props.statusBarShowProgress}
        infoItems={props.statusBarInfoItems}
        onInfoClick={props.onStatusInfoClick}
        trailing={
          <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.25 }}>
            <ActiveVerificationsMonitor
              api={props.editorHandle?.getApi() ?? null}
              connected={props.connectionStatus === 'connected'}
              onActivate={props.onOpenVerificationsTab}
            />
            {props.portfolios.length > 0 && (
              <PortfolioSettings
                portfolios={props.portfolios}
                verifyPortfolioId={props.portfolioId}
                onVerifyPortfolioChange={props.onPortfolioChange}
                validationPortfolioId={
                  props.flavorInfo?.validateWitnessCommand ? props.validationPortfolioId : null
                }
                onValidationPortfolioChange={props.onValidationPortfolioChange}
              />
            )}
          </Box>
        }
      />
      <DevInfoPanel
        open={props.devPanelOpen}
        onClose={props.onCloseDevPanel}
        connectionStatus={props.connectionStatus}
        language={props.language}
        connectedSince={props.connectedSince}
        reconnectCount={props.reconnectCount}
        editorHandle={props.editorHandle}
        backendUrl={props.backendUrl}
      />
    </>
  )
}
