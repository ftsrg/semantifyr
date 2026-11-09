/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { normalizeBaseUrl, type FlavorsResponse } from '@semantifyr/editor-common'
import type {
  AdminConfigResponse,
  AdminStatusResponse,
  AdminWhoamiResponse,
  FlavorInfo,
  InfoResponse,
  PortfoliosResponse,
} from './types'

export interface LiveServerApi {
  readonly httpBase: string

  fetchPortfolios(): Promise<PortfoliosResponse>
  fetchFlavors(): Promise<FlavorInfo[]>
  fetchFlavor(id: string): Promise<FlavorInfo | null>
  fetchInfo(): Promise<InfoResponse>
  fetchAdminUser(): Promise<string>
  fetchAdminConfig(): Promise<AdminConfigResponse>
  fetchAdminStatus(): Promise<AdminStatusResponse>
  cancelSession(sessionId: string): Promise<boolean>
  cancelVerification(verificationId: string): Promise<boolean>
}

function withBase(httpBase: string, path: string): string {
  return `${httpBase}${path}`
}

async function expectOk<T>(response: Response): Promise<T> {
  if (response.status === 401) {
    throw new Error('Signed out, reload to sign in')
  }
  if (response.status === 403) {
    throw new Error('Not signed in through the proxy')
  }
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return response.json() as Promise<T>
}

export function createApi(rawBackendUrl: string): LiveServerApi {
  const { http: httpBase } = normalizeBaseUrl(rawBackendUrl)

  const get = (path: string, init?: RequestInit) => fetch(withBase(httpBase, path), init)

  return {
    httpBase,

    async fetchPortfolios() {
      const response = await get('/api/portfolios')
      return expectOk<PortfoliosResponse>(response)
    },

    async fetchFlavors() {
      const response = await get('/api/flavors')
      return (await expectOk<FlavorsResponse>(response)).flavors
    },

    async fetchFlavor(id) {
      const flavors = await this.fetchFlavors()
      return flavors.find((f) => f.id === id) ?? null
    },

    async fetchInfo() {
      const response = await get('/api/info')
      return expectOk<InfoResponse>(response)
    },

    async fetchAdminUser() {
      const response = await get('/api/admin/whoami')
      return (await expectOk<AdminWhoamiResponse>(response)).user
    },

    async fetchAdminConfig() {
      const response = await get('/api/admin/config')
      return expectOk<AdminConfigResponse>(response)
    },

    async fetchAdminStatus() {
      const response = await get('/api/admin/status')
      return expectOk<AdminStatusResponse>(response)
    },

    async cancelSession(sessionId) {
      const response = await get(`/api/admin/sessions/${encodeURIComponent(sessionId)}`, {
        method: 'DELETE',
      })
      return response.ok
    },

    async cancelVerification(verificationId) {
      const response = await get(`/api/admin/verifications/${encodeURIComponent(verificationId)}`, {
        method: 'DELETE',
      })
      return response.ok
    },
  }
}
