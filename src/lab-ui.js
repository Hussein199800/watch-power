import { simulateDevice } from './lab.js';
import { latinDigits } from './model.js';
export function viewSimulation(h) {
  const c = h.state.simulation ||= { watts: 1000, minutes: 60, alreadyOn: false };
  return `<section class="card" id="simulation"><h2>محاكاة جهاز على الحمل الحالي</h2>
    <div class="form"><label class="num-row">قدرة الجهاز (واط)<input id="simWatts" inputmode="decimal" type="text" value="${c.watts}"></label>
    <label class="num-row">مدة التشغيل (دقيقة)<input id="simMinutes" inputmode="decimal" type="text" value="${c.minutes}"></label>
    <label><input id="simAlreadyOn" type="checkbox" ${c.alreadyOn ? 'checked' : ''}> الجهاز يعمل الآن ومشمول في قراءة الحمل</label></div>
    <div id="simResult" role="status" aria-live="polite"></div>
    <p class="hint">افتراضات: قدرة ثابتة طوال المدة، وثبات الحمل وإنتاج الألواح ووضع التشغيل. كفاءة التحويل 90%. الطاقة تخص الجهاز وحده؛ إذا كان يعمل أصلاً فالإضافة صفر. لا يشمل تيار الإقلاع أو حدود القدرة الظاهرية. أثر البطارية تقدير خطي في وضع البطارية فقط، ويتطلب نسبة الشحن وسعتها وجهدها المقنّن. لا تُرسل أي أوامر.</p></section>`;
}
export function bindSimulation(h) {
  const root = h.$('#simulation'); if (!root) return;
  const c = h.state.simulation;
  const update = () => {
    c.watts = Number(latinDigits(h.$('#simWatts').value));
    c.minutes = Number(latinDigits(h.$('#simMinutes').value));
    c.alreadyOn = h.$('#simAlreadyOn').checked;
    const r = simulateDevice(h.state.model, { ...c, ...h.state.prefs });
    const f = v => Number.isFinite(v) ? v.toFixed(2) : '—';
    h.$('#simResult').textContent = r.error || `طاقة الجهاز: ${f(r.energyWh / 1000)} ك.و.س. ` + (r.unavailable ||
      `الحمل المتوقع: ${f(r.totalW)} واط. الإضافة: ${f(r.addedW)} واط / ${f(r.addedWh / 1000)} ك.و.س. هامش الإنفرتر: ${f(r.headroomW)} واط${r.headroomW < 0 ? ' — تجاوز القدرة المقنّنة' : ''}. ` +
      (r.battery ? `البطارية: ${f(r.battery.projectedBatW)} واط (الموجب شحن)، الشحن المتوقع: ${f(r.battery.projectedSoc)}%، النقص الإضافي: ${f(r.battery.extraSocDrop)} نقطة مئوية.${r.battery.belowReserve ? ' أقل من الاحتياطي المحدد.' : ''}${r.battery.outsideCapacity ? ' يتجاوز حدود السعة؛ لن يستمر هذا المعدل طوال المدة.' : ''}` : 'أثر البطارية غير متاح: يلزم وضع البطارية وقراءات الشحن والقدرة والسعة والجهد المقنّن.'));
  };
  root.querySelectorAll('input').forEach(el => el.addEventListener('input', update)); update();
}
