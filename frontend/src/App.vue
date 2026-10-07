<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AvatarControls from './components/AvatarControls.vue'
import { LOGO_PRIVSEX, LOGO_TG_BLUE } from './logos'

const DEFAULT_API_BASE = 'https://sgolmmhbufosmtigaakx.supabase.co/functions/v1/privsex-links-api'
const configuredApiBase = String(import.meta.env.VITE_API_BASE_URL || '').trim()
const API_BASE = (configuredApiBase && !/localhost|127\.0\.0\.1/i.test(configuredApiBase) ? configuredApiBase : DEFAULT_API_BASE).replace(/\/$/, '')
const apiUrl = (path: string) => `${API_BASE}${path}`
const PRIVSEX_URL = 'https://privsex.com/juliasalles'
const PUBLIC_CHANNEL_URL = 'https://t.me/+VFz27CGP9IczMmUx'
const isManagement = window.location.pathname === '/gestao' || window.location.pathname === '/gestão'
const editMode = new URLSearchParams(window.location.search).get('edit_avatar') === '1'

const avatarPosition = reactive({ x: 50, y: 50, zoom: 1 })
const adminAuthed = ref(false)
const editorOpen = ref(isManagement || editMode)
const avatarSaving = ref(false)
const avatarSaveMessage = ref('')
const avatarSaveError = ref('')
const checkingSession = ref(true)
const adminPassword = ref('')
const loginError = ref('')
const loginLoading = ref(false)

const avatarUrl = computed(() => apiUrl('/api/avatar-image'))
const showLandingEditor = computed(() => !isManagement && adminAuthed.value && editMode)

function applyAvatarPosition(saved: any) {
  if (Number.isFinite(Number(saved?.x))) avatarPosition.x = Math.min(100, Math.max(0, Number(saved.x)))
  if (Number.isFinite(Number(saved?.y))) avatarPosition.y = Math.min(100, Math.max(0, Number(saved.y)))
  if (Number.isFinite(Number(saved?.zoom))) avatarPosition.zoom = Math.min(2, Math.max(1, Number(saved.zoom)))
}

function avatarTransform() {
  return `translate(${Number(avatarPosition.x) - 50}%, ${Number(avatarPosition.y) - 50}%) scale(${Number(avatarPosition.zoom)})`
}

async function request(path: string, init: RequestInit = {}) {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const response = await fetch(apiUrl(path), { ...init, headers, credentials: 'include' })
  if (!response.ok) {
    const raw = await response.text()
    let detail = raw
    try { detail = JSON.parse(raw)?.message || raw } catch {}
    throw new Error(detail || `HTTP ${response.status}`)
  }
  return response.status === 204 ? null : response.json()
}

async function loadPosition() {
  try { applyAvatarPosition(await request('/api/avatar-position?ts=' + Date.now())) } catch {}
}

async function checkSession() {
  try {
    await request('/api/admin/session')
    adminAuthed.value = true
  } catch {
    adminAuthed.value = false
  } finally {
    checkingSession.value = false
  }
}

async function login() {
  loginLoading.value = true
  loginError.value = ''
  try {
    await request('/api/admin/login', { method: 'POST', body: JSON.stringify({ password: adminPassword.value }) })
    adminPassword.value = ''
    adminAuthed.value = true
    editorOpen.value = true
  } catch (error: any) {
    loginError.value = error?.message || 'Senha inválida.'
  } finally {
    loginLoading.value = false
  }
}

async function savePosition() {
  avatarSaving.value = true
  avatarSaveMessage.value = ''
  avatarSaveError.value = ''
  try {
    await request('/api/admin/avatar-position', {
      method: 'POST',
      body: JSON.stringify({ x: avatarPosition.x, y: avatarPosition.y, zoom: avatarPosition.zoom }),
    })
    applyAvatarPosition(await request('/api/avatar-position?ts=' + Date.now()))
    avatarSaveMessage.value = 'Posição salva no Supabase.'
  } catch (error: any) {
    avatarSaveError.value = error?.message || 'Não foi possível salvar a posição.'
  } finally {
    avatarSaving.value = false
  }
}

async function logout() {
  try { await request('/api/admin/logout', { method: 'POST' }) } catch {}
  adminAuthed.value = false
  editorOpen.value = false
}

onMounted(async () => {
  await loadPosition()
  await checkSession()
})
</script>

<template>
  <main class="page">
    <div class="glow" aria-hidden="true"></div>
    <div class="grain" aria-hidden="true"></div>

    <section v-if="isManagement" class="management" aria-labelledby="management-title">
      <template v-if="checkingSession">
        <p class="management-muted">Verificando acesso…</p>
      </template>
      <template v-else-if="!adminAuthed">
        <p class="eyebrow">Área restrita</p>
        <h1 id="management-title">Gestão do avatar</h1>
        <p class="management-muted">Entre com a senha administrativa para ajustar a logo da landing.</p>
        <form class="login-form" @submit.prevent="login">
          <input v-model="adminPassword" type="password" autocomplete="current-password" placeholder="Senha do administrador" required />
          <button type="submit" :disabled="loginLoading">{{ loginLoading ? 'Entrando…' : 'Entrar' }}</button>
        </form>
        <p v-if="loginError" class="error" role="alert">{{ loginError }}</p>
      </template>
      <template v-else>
        <div class="management-header">
          <div><p class="eyebrow">Área restrita</p><h1 id="management-title">Ajustar avatar</h1></div>
          <button class="ghost" type="button" @click="logout">Sair</button>
        </div>
        <div class="avatar avatar--large">
          <img :src="avatarUrl" alt="Pré-visualização do avatar PrivSex" :style="{ transform: avatarTransform() }" />
        </div>
      <AvatarControls :position="avatarPosition" :saving="avatarSaving" :message="avatarSaveMessage" :error="avatarSaveError" @change="Object.assign(avatarPosition, $event)" @save="savePosition" />
      </template>
    </section>

    <section v-else class="shell" aria-labelledby="title">
      <div class="avatar" aria-hidden="true">
        <img :src="avatarUrl" alt="Logo PrivSex" :style="{ transform: avatarTransform() }" />
      </div>
      <button v-if="showLandingEditor" type="button" class="edit-trigger" @click="editorOpen = !editorOpen">
        {{ editorOpen ? 'Fechar ajuste' : 'Ajustar avatar' }}
      </button>
      <AvatarControls v-if="showLandingEditor && editorOpen" :position="avatarPosition" :saving="avatarSaving" :message="avatarSaveMessage" :error="avatarSaveError" @change="Object.assign(avatarPosition, $event)" @save="savePosition" />

      <p class="eyebrow">PrivSex</p>
      <h1 id="title">Sua conexão com criadores online</h1>
      <p class="subtitle">Conheça a plataforma e escolha como quer continuar.</p>

      <nav class="links" aria-label="Links oficiais do PrivSex">
        <a class="card card--privsex" :href="PRIVSEX_URL" target="_blank" rel="noopener noreferrer">
          <span class="icon"><img :src="LOGO_PRIVSEX" alt="" width="30" height="30" /></span><span class="copy"><strong>Entrar no PrivSex</strong><small>Encontre criadores, chat e conteúdo exclusivo</small></span><span class="arrow">↗</span>
        </a>
        <a class="card card--telegram" :href="PUBLIC_CHANNEL_URL" target="_blank" rel="noopener noreferrer">
          <span class="portal-spiral" aria-hidden="true">
            <span class="ps-ring ps-r1"></span><span class="ps-ring ps-r2"></span>
            <span class="ps-ring ps-r3"></span><span class="ps-ring ps-r4"></span>
            <span class="ps-core"></span>
          </span>
          <span class="icon icon--tg"><img :src="LOGO_TG_BLUE" alt="" width="30" height="30" /></span><span class="copy"><strong>Canal Público</strong><small>Quer conhecer as criadoras? Acesse nosso canal público no Telegram</small></span><span class="arrow">↗</span>
        </a>
      </nav>

      <section class="info" aria-labelledby="about">
        <div><h2 id="about">O que é o PrivSex?</h2><p>O PrivSex é uma rede social global que conecta criadores a seus fãs online.</p></div>
        <div><h2>Como usar a plataforma</h2><p>Crie uma conta com e-mail, Google ou X. Você também pode testar entrando como visitante.</p></div>
        <div><h2>O que você encontra</h2><ul><li>Chat com sua criadora favorita</li><li>Conteúdo exclusivo</li><li>Lives ao vivo para inscritos</li><li>Posts individuais no feed</li><li>Videochamadas</li><li>E muito mais</li></ul></div>
      </section>
      <p class="footer">© 2026 PrivSex. Todos os direitos reservados.</p>
    </section>
  </main>
</template>
