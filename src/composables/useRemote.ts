import { onScopeDispose, shallowRef, watch, type WatchSource } from 'vue'
import { ProjectApiError } from '../api/projects'

/** Each reload replaces its predecessor; errors never turn into successful empty data. */
export function useRemote<T>(key: WatchSource<unknown>, loader: (signal: AbortSignal) => Promise<T>) {
  const data = shallowRef<T | null>(null)
  const loading = shallowRef(true)
  const error = shallowRef('')
  const notFound = shallowRef(false)
  let request = 0
  let controller: AbortController | undefined

  async function reload() {
    const current = ++request
    controller?.abort()
    controller = new AbortController()
    const signal = controller.signal
    loading.value = true
    data.value = null
    error.value = ''
    notFound.value = false
    try {
      const result = await loader(signal)
      if (current === request && !signal.aborted) data.value = result
    } catch (failure) {
      if (current !== request || signal.aborted) return
      notFound.value = failure instanceof ProjectApiError && failure.notFound
      error.value = failure instanceof ProjectApiError ? failure.message : '作品暫時無法載入，請稍後重試。'
    } finally {
      if (current === request && !signal.aborted) loading.value = false
    }
  }
  watch(key, reload, { immediate: true })
  onScopeDispose(() => { request++; controller?.abort() })
  return { data, loading, error, notFound, reload }
}
