import { storeToRefs } from "pinia";
import { onMounted, ref } from "vue";
import { getFeishuLoginSubjects } from "@/api/employees";
import { parsePasswordLoginEnabled, useAuthLoginStore } from "@/stores/authLogin";
import type { FeishuSubjectOption } from "@/types/api.interface";

/** 账密登录开关（system_config.password_login_enabled），全局 store 缓存 */
export function usePasswordLoginEnabled(options?: { loadFeishuSubjects?: boolean }) {
  const authLogin = useAuthLoginStore();
  const { passwordLoginEnabled, loaded } = storeToRefs(authLogin);
  const feishuSubjects = ref<FeishuSubjectOption[]>([]);

  onMounted(() => {
    void (async () => {
      if (!authLogin.loaded) {
        await authLogin.refresh();
      }
      if (options?.loadFeishuSubjects) {
        try {
          const res = await getFeishuLoginSubjects();
          feishuSubjects.value = res.items || [];
          authLogin.passwordLoginEnabled = parsePasswordLoginEnabled(res.passwordLoginEnabled);
        } catch {
          feishuSubjects.value = [];
        }
      }
    })();
  });

  return { passwordLoginEnabled, loaded, feishuSubjects, refresh: () => authLogin.refresh() };
}
