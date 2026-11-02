import { DOMWrapper, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import App from '../../src/App.vue'
import appRouter from '../../src/router'

export interface RenderedApp {
  wrapper: VueWrapper
  router: Router
}

const mounted: VueWrapper[] = []

async function routerAt(path: string): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: appRouter.options.routes,
  })
  await router.push(path)
  await router.isReady()
  return router
}

export async function renderApp(path: string): Promise<RenderedApp> {
  const router = await routerAt(path)
  const wrapper = mount(App, { global: { plugins: [router] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, router }
}

export interface MountedViewModel<T> {
  viewModel: T
  router: Router
}

export async function mountViewModel<T>(
  path: string,
  useViewModel: () => T,
): Promise<MountedViewModel<T>> {
  const router = await routerAt(path)
  let viewModel: T | undefined
  const host = defineComponent({
    setup() {
      viewModel = useViewModel()
      return () => null
    },
  })
  mounted.push(mount(host, { global: { plugins: [router] } }))
  await flushPromises()
  return { viewModel: viewModel as T, router }
}

export function unmountAll(): void {
  for (const wrapper of mounted.splice(0)) wrapper.unmount()
}

export function control<T extends Element = HTMLInputElement>(
  scope: VueWrapper | DOMWrapper<Element>,
  labelText: string,
): DOMWrapper<T> {
  const label = scope.findAll('label').find((l) => l.text() === labelText)
  if (label && !label.attributes('for')) {
    const wrapped = label.find<T>('input, select, textarea')
    if (!wrapped.exists()) throw new Error(`No control labelled "${labelText}"`)
    return wrapped
  }
  const selector = label
    ? `[id="${label.attributes('for')}"]`
    : `[aria-label="${labelText}"]`
  const element = scope.find<T>(selector)
  if (!element.exists()) throw new Error(`No control labelled "${labelText}"`)
  return element
}

function referencedText(scope: VueWrapper | DOMWrapper<Element>, ids: string | undefined): string {
  return (ids ?? '')
    .split(/\s+/)
    .filter(Boolean)
    .map((id) => scope.find(`[id="${id}"]`))
    .filter((element) => element.exists())
    .map((element) => element.text())
    .join(' ')
}

export function fieldOf(
  scope: VueWrapper | DOMWrapper<Element>,
  labelText: string,
): { text(): string } {
  const input = control(scope, labelText)
  return { text: () => referencedText(scope, input.attributes('aria-describedby')) }
}

export function section(scope: VueWrapper, heading: string): DOMWrapper<Element> {
  const match = scope
    .findAll('[aria-labelledby]')
    .filter((s) => s.element.tagName === 'SECTION' || s.attributes('role') === 'region')
    .find((s) => referencedText(s, s.attributes('aria-labelledby')) === heading)
  if (!match) throw new Error(`No region labelled "${heading}"`)
  return match
}

export function button(scope: VueWrapper | DOMWrapper<Element>, text: string): DOMWrapper<HTMLButtonElement> {
  const match = scope.findAll<HTMLButtonElement>('button').find((b) => b.text() === text)
  if (!match) throw new Error(`No button "${text}"`)
  return match
}

export function link(scope: VueWrapper | DOMWrapper<Element>, text: string): DOMWrapper<HTMLAnchorElement> {
  const match = scope.findAll<HTMLAnchorElement>('a').find((a) => a.text() === text)
  if (!match) throw new Error(`No link "${text}"`)
  return match
}

export async function pickFiles(input: DOMWrapper<HTMLInputElement>, files: File[]): Promise<void> {
  Object.defineProperty(input.element, 'files', { value: files, configurable: true })
  await input.trigger('change')
}
