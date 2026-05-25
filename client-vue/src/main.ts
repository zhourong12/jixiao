import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import router from "./router";
import { useAuthLoginStore } from "@/stores/authLogin";
import "./styles/main.css";

const pinia = createPinia();
const app = createApp(App);
app.use(pinia);
app.use(router);
void useAuthLoginStore().refresh();
app.mount("#app");
