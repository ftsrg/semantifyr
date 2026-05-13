/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { normalizeBaseUrl } from './urls'
import type {
  AdminConfigResponse,
  AdminStatusResponse,
  FlavorInfo,
  InfoResponse,
  PortfolioInfo,
} from './types'

interface PortfoliosResponse {
  portfolios: PortfolioInfo[]
}

interface FlavorsResponse {
  flavors: FlavorInfo[]
}

export interface LiveServerApi {
  readonly httpBase: string

  fetchPortfolios(): Promise<PortfolioInfo[]>
  fetchFlavors(): Promise<FlavorInfo[]>
  fetchFlavor(id: string): Promise<FlavorInfo | null>
  fetchInfo(): Promise<InfoResponse>

  // Cookie-gated.
  loginAdmin(password: string): Promise<boolean>
  logoutAdmin(): Promise<void>
  checkAdminAuth(): Promise<boolean>
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
    throw new Error('Unauthorized')
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
      if (!response.ok) {
        return []
      }
      const data = (await response.json()) as PortfoliosResponse
      return data.portfolios
    },

    async fetchFlavors() {
      const response = await get('/api/flavors')
      if (!response.ok) {
        return []
      }
      const data = (await response.json()) as FlavorsResponse
      return data.flavors
    },

    async fetchFlavor(id) {
      const flavors = await this.fetchFlavors()
      return flavors.find((f) => f.id === id) ?? null
    },

    async fetchInfo() {
      const response = await get('/api/info')
      return expectOk<InfoResponse>(response)
    },

    async loginAdmin(password) {
      const response = await get('/api/admin/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password }),
      })
      return response.ok
    },

    async logoutAdmin() {
      await get('/api/admin/logout', { method: 'POST' })
    },

    async checkAdminAuth() {
      const response = await get('/api/admin/whoami')
      return response.ok
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
