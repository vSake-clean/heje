/* =========================================================================
   HackLingo – silnik aplikacji (widoki, postęp, grywalizacja)
   ========================================================================= */
(function () {
  "use strict";

  const root = document.getElementById("app");
  const tabbar = document.getElementById("tabbar");
  const STORE = "hacklingo_v1";
  const MAX_HEARTS = 5;
  const HEART_REGEN_MS = 20 * 60 * 1000; // 1 serce co 20 min

  /* ---------- stan ---------- */
  function defState() {
    return { xp: 0, streak: 0, lastDay: null, done: {}, hearts: MAX_HEARTS, heartsAt: Date.now() };
  }
  let P = load();
  function load() {
    try {
      const s = JSON.parse(localStorage.getItem(STORE));
      if (s && typeof s === "object") return Object.assign(defState(), s);
    } catch (e) {}
    return defState();
  }
  function save() { try { localStorage.setItem(STORE, JSON.stringify(P)); } catch (e) {} }

  /* ---------- czas / serce ---------- */
  function dayStamp(offset) {
    const d = new Date();
    d.setDate(d.getDate() + (offset || 0));
    return d.toISOString().slice(0, 10);
  }
  function syncHearts() {
    if (P.hearts >= MAX_HEARTS) { P.heartsAt = Date.now(); return; }
    const gained = Math.floor((Date.now() - (P.heartsAt || Date.now())) / HEART_REGEN_MS);
    if (gained > 0) {
      P.hearts = Math.min(MAX_HEARTS, P.hearts + gained);
      P.heartsAt = (P.hearts >= MAX_HEARTS) ? Date.now() : (P.heartsAt + gained * HEART_REGEN_MS);
      save();
    }
  }
  function loseHeart() {
    syncHearts();
    if (P.hearts === MAX_HEARTS) P.heartsAt = Date.now();
    P.hearts = Math.max(0, P.hearts - 1);
    save();
  }
  function gainHeart(n) {
    syncHearts();
    P.hearts = Math.min(MAX_HEARTS, P.hearts + (n || 1));
    save();
  }

  function level() { return Math.floor(P.xp / 50) + 1; }

  /* ---------- flatten lekcji (kolejność odblokowywania) ---------- */
  const FLAT = [];
  COURSE.forEach((u) => u.lessons.forEach((l) => FLAT.push({ unit: u, lesson: l })));
  const isDone = (id) => !!P.done[id];
  const flatIndex = (id) => FLAT.findIndex((f) => f.lesson.id === id);
  const isUnlocked = (i) => i === 0 || isDone(FLAT[i - 1].lesson.id);

  /* ---------- router ---------- */
  let view = "path";
  let L = null; // runtime aktywnej lekcji / treningu

  function setView(v) { view = v; L = null; render(); }

  /* ---------- UI helpers ---------- */
  function h(tag, cls, html) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (html != null) e.innerHTML = html;
    return e;
  }
  function toast(msg) {
    const t = h("div", "toast", msg);
    document.body.appendChild(t);
    setTimeout(() => t.remove(), 1800);
  }
  function confetti() {
    const c = h("div", "confetti");
    const emo = ["🎉", "⭐", "🟩", "🔥", "🏆", "💚", "✨"];
    for (let i = 0; i < 26; i++) {
      const s = h("span", null, emo[i % emo.length]);
      s.style.cssText =
        "position:absolute;left:" + Math.random() * 100 + "%;top:-24px;font-size:" +
        (16 + Math.random() * 20) + "px;transition:transform 1.25s ease-in,opacity 1.25s;";
      c.appendChild(s);
    }
    document.body.appendChild(c);
    requestAnimationFrame(() => {
      [].forEach.call(c.children, (s) => {
        s.style.transform = "translateY(" + (window.innerHeight + 60) + "px) rotate(" + (Math.random() * 540) + "deg)";
        s.style.opacity = "0";
      });
    });
    setTimeout(() => c.remove(), 1400);
  }

  function topbar() {
    syncHearts();
    const bar = h("div", "topbar");
    bar.appendChild(h("div", "stat streak", "<span class='ico'>🔥</span>" + (P.streak || 0)));
    bar.appendChild(h("div", "stat xp", "<span class='ico'>⭐</span>" + P.xp));
    bar.appendChild(h("div", "stat hearts", "<span class='ico'>❤️</span>" + P.hearts));
    return bar;
  }

  /* ======================================================================
     WIDOK: ścieżka nauki
     ====================================================================== */
  function renderPath() {
    root.innerHTML = "";
    root.appendChild(topbar());

    const intro = h("div", "ethics",
      "🛡️ <b>HackLingo</b> — uczysz się na <b>symulowanych</b> podatnościach w tej aplikacji. " +
      "Zdobytą wiedzę wykorzystuj tylko legalnie: do obrony i do testów za zgodą właściciela.");
    root.appendChild(intro);

    let gi = 0;
    COURSE.forEach((unit) => {
      const head = h("div", "unit-head");
      head.style.background = unit.color;
      head.style.boxShadow = "0 4px 0 rgba(0,0,0,.18)";
      const left = h("div");
      left.appendChild(h("div", "u-title", unit.icon + " " + unit.title));
      left.appendChild(h("div", "u-sub", unit.short));
      head.appendChild(left);
      root.appendChild(head);

      const path = h("div", "path");
      unit.lessons.forEach((lesson) => {
        const idx = flatIndex(lesson.id);
        const unlocked = isUnlocked(idx);
        const done = isDone(lesson.id);
        const current = unlocked && !done;

        const wrap = h("div", "node-wrap o" + ((gi % 7) + 1));
        const node = h("button", "node");
        if (unit.boss) node.classList.add("boss");
        if (done) node.classList.add("done");
        if (!unlocked) node.classList.add("locked");
        if (current) node.classList.add("current");

        node.innerHTML = "<span class='ring'></span>" +
          (done ? "✓" : (!unlocked ? "🔒" : (unit.boss ? unit.icon : "★")));

        if (current) {
          const flag = h("div", "start-flag", "Start");
          wrap.appendChild(flag);
        } else {
          wrap.appendChild(h("div", "node-label", lesson.title));
        }

        node.onclick = () => {
          if (!unlocked) { toast("🔒 Najpierw ukończ poprzednią lekcję"); return; }
          syncHearts();
          if (P.hearts <= 0 && !done) {
            noHearts();
            return;
          }
          startLesson(unit, lesson);
        };
        wrap.appendChild(node);
        path.appendChild(wrap);
        gi++;
      });
      root.appendChild(path);
    });

    const prog = Object.keys(P.done).length;
    root.appendChild(h("div", "center muted",
      "Ukończono " + prog + " / " + FLAT.length + " lekcji · poziom " + level()));
    root.appendChild(h("div", "spacer"));
  }

  function noHearts() {
    root.innerHTML = "";
    root.appendChild(topbar());
    const c = h("div", "card center");
    c.appendChild(h("div", "big-emoji", "💔"));
    c.appendChild(h("h2", null, "Serca się skończyły"));
    c.appendChild(h("p", "muted",
      "Serca odnawiają się z czasem (1 co 20 min). Możesz też zdobyć je w <b>Treningu</b> — za każdą poprawną odpowiedź dostajesz serce."));
    const b = h("button", "btn gold", "🎯 Idź na trening");
    b.onclick = () => setView("practice");
    c.appendChild(b);
    const b2 = h("button", "btn ghost", "Wróć");
    b2.style.marginTop = "10px";
    b2.onclick = () => setView("path");
    c.appendChild(b2);
    root.appendChild(c);
  }

  /* ======================================================================
     LEKCJA
     ====================================================================== */
  function startLesson(unit, lesson) {
    L = {
      mode: "lesson",
      unit: unit, lesson: lesson,
      steps: lesson.steps, idx: 0,
      sel: null, hintShown: 0, stepDone: false,
      correct: 0, wrong: 0
    };
    tabbar.classList.add("hidden");
    renderStep();
  }

  function lessonTop() {
    const bar = h("div", "lesson-top");
    const x = h("button", "close-x", "✕");
    x.onclick = () => {
      if (confirm("Przerwać lekcję? Postęp w tej lekcji nie zostanie zapisany.")) {
        tabbar.classList.remove("hidden");
        setView("path");
      }
    };
    bar.appendChild(x);
    const prog = h("div", "progress");
    const pct = Math.round((L.idx / L.steps.length) * 100);
    prog.appendChild(h("i")).style.width = pct + "%";
    bar.appendChild(prog);
    bar.appendChild(h("div", "heart-mini", "❤️ " + P.hearts));
    return bar;
  }

  function renderStep() {
    removeBanner();
    root.innerHTML = "";
    root.appendChild(lessonTop());
    const step = L.steps[L.idx];
    const s = h("div", "step");
    root.appendChild(s);
    const foot = h("div"); foot.id = "lf"; foot.style.marginTop = "14px";
    root.appendChild(foot);

    if (step.type === "info") return renderInfo(step, s, foot);
    if (step.type === "quiz") return renderQuiz(step, s, foot);
    if (step.type === "challenge") return renderChallenge(step, s, foot);
  }

  function renderInfo(step, s, foot) {
    s.appendChild(h("h2", null, step.title || "Info"));
    s.appendChild(h("div", "card", step.html));
    const b = h("button", "btn", "Dalej");
    b.onclick = next;
    foot.appendChild(b);
  }

  function hintButton(step, s, foot) {
    if (!step.hints || !step.hints.length) return;
    const hb = h("button", "btn hint-btn small", "💡 Podpowiedź");
    hb.style.width = "auto";
    hb.onclick = () => {
      if (L.hintShown >= step.hints.length) { toast("To już ostatnia podpowiedź 🙂"); return; }
      const box = h("div", "hint-box");
      box.appendChild(h("div", "h-step", "Podpowiedź " + (L.hintShown + 1) + " / " + step.hints.length));
      box.appendChild(h("div", null, step.hints[L.hintShown]));
      s.appendChild(box);
      L.hintShown++;
      box.scrollIntoView({ behavior: "smooth", block: "center" });
      if (L.hintShown >= step.hints.length) hb.textContent = "💡 (koniec podpowiedzi)";
    };
    const row = h("div"); row.style.cssText = "display:flex;justify-content:flex-start";
    row.appendChild(hb);
    foot.appendChild(row);
  }

  function renderQuiz(step, s, foot) {
    s.appendChild(h("div", "prompt-q", step.q));
    const opts = [];
    step.options.forEach((o, i) => {
      const b = h("button", "opt", o);
      b.onclick = () => {
        if (L.stepDone) return;
        opts.forEach((x) => x.classList.remove("sel"));
        b.classList.add("sel");
        L.sel = i;
        chk.disabled = false;
      };
      opts.push(b);
      s.appendChild(b);
    });

    hintButton(step, s, foot);
    const chk = h("button", "btn", "Sprawdź");
    chk.disabled = true;
    chk.onclick = () => {
      if (L.sel == null || L.stepDone) return;
      L.stepDone = true;
      const ok = L.sel === step.answer;
      opts.forEach((x, i) => {
        x.disabled = true;
        if (i === step.answer) x.classList.add("correct");
        else if (i === L.sel) x.classList.add("wrong");
      });
      if (ok) { L.correct++; }
      else { L.wrong++; loseHeart(); }
      banner(ok, ok ? "Świetnie!" : "Niezupełnie",
        step.explain || "", ok);
    };
    foot.appendChild(chk);
  }

  function renderChallenge(step, s, foot) {
    s.appendChild(h("h2", null, "🧪 " + (step.title || "Laboratorium")));
    const brief = h("div", "card");
    brief.innerHTML = step.brief || "";
    const tag = h("span", "pill safe", "🔒 symulacja — bezpieczne");
    brief.appendChild(h("div")).appendChild(tag);
    s.appendChild(brief);

    const factory = LABS[step.kind];
    if (!factory) { s.appendChild(h("div", "card", "Brak laboratorium: " + step.kind)); return; }
    const labEl = factory(step, {
      onSuccess: (detail) => {
        if (L.stepDone) return;
        L.stepDone = true; L.correct++;
        const txt = (detail && detail.message ? detail.message : "") +
          (detail && detail.defense ? "<br><br><span class='pill info'>obrona</span> " + detail.defense : "");
        banner(true, "🎯 Podatność wykorzystana!", txt, true);
      }
    });
    s.appendChild(labEl);
    hintButton(step, s, foot);
  }

  /* ---------- feedback banner ---------- */
  function removeBanner() {
    const b = document.querySelector(".feedback");
    if (b) b.remove();
    const lf = document.getElementById("lf");
    if (lf) lf.style.visibility = "";
  }
  function banner(ok, title, html, advance) {
    removeBanner();
    const lf = document.getElementById("lf");
    const fb = h("div", "feedback " + (ok ? "ok" : "bad"));
    const inner = h("div", "fb-inner");
    inner.appendChild(h("div", "fb-title", (ok ? "✅ " : "❌ ") + title));
    if (html) inner.appendChild(h("div", "fb-text", html));
    const cont = h("button", "btn " + (ok ? "" : "danger"), "Kontynuuj");
    cont.onclick = next;
    inner.appendChild(cont);
    fb.appendChild(inner);
    document.body.appendChild(fb);
    cont.focus();
  }

  function next() {
    removeBanner();
    L.idx++;
    L.sel = null; L.hintShown = 0; L.stepDone = false;
    if (L.idx >= L.steps.length) finishLesson();
    else renderStep();
  }

  function finishLesson() {
    const lesson = L.lesson;
    const first = !isDone(lesson.id);
    const award = first ? lesson.xp : Math.max(5, Math.round(lesson.xp / 3));
    P.xp += award;
    P.done[lesson.id] = true;
    gainHeart(1);

    // seria
    const today = dayStamp(0);
    if (P.lastDay !== today) {
      P.streak = (P.lastDay === dayStamp(-1)) ? (P.streak || 0) + 1 : 1;
      P.lastDay = today;
    }
    save();

    const newBadges = BADGES.filter((b) => b.test(P) && !P["badge_" + b.id]);
    newBadges.forEach((b) => (P["badge_" + b.id] = true));
    save();

    confetti();
    root.innerHTML = "";
    const c = h("div", "card center");
    c.appendChild(h("div", "big-emoji", L.unit.boss ? "🏆" : "🎉"));
    c.appendChild(h("h1", null, "Lekcja ukończona!"));
    c.appendChild(h("div", "center muted", lesson.title));
    const grid = h("div", "stat-grid");
    grid.appendChild(statCard("+" + award, "zdobyte XP", "var(--gold-d)"));
    grid.appendChild(statCard(L.correct + "/" + (L.correct + L.wrong || L.correct), "poprawne", "var(--green-d)"));
    c.appendChild(grid);
    if (newBadges.length) {
      c.appendChild(h("div", "center", "Nowa odznaka: " +
        newBadges.map((b) => b.ico + " " + b.name).join(", ")));
    }
    const b = h("button", "btn", "Dalej");
    b.onclick = () => { tabbar.classList.remove("hidden"); setView("path"); };
    c.appendChild(b);
    root.appendChild(c);
  }

  function statCard(big, lbl, color) {
    const c = h("div", "stat-card center");
    const b = h("div", "big", big); if (color) b.style.color = color;
    c.appendChild(b);
    c.appendChild(h("div", "lbl", lbl));
    return c;
  }

  /* ======================================================================
     WIDOK: trening (szybkie pytania, nagroda = serca + XP)
     ====================================================================== */
  function renderPractice() {
    const pool = [];
    COURSE.forEach((u) => u.lessons.forEach((l) => l.steps.forEach((st) => {
      if (st.type === "quiz") pool.push(st);
    })));
    for (let i = pool.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [pool[i], pool[j]] = [pool[j], pool[i]];
    }
    L = { mode: "practice", steps: pool.slice(0, 8), idx: 0, sel: null, stepDone: false, correct: 0, wrong: 0 };
    renderPracticeStep();
  }

  function renderPracticeStep() {
    removeBanner();
    root.innerHTML = "";
    if (L.idx >= L.steps.length) return practiceSummary();

    const top = h("div", "lesson-top");
    const x = h("button", "close-x", "✕");
    x.onclick = () => setView("path");
    top.appendChild(x);
    const prog = h("div", "progress");
    prog.appendChild(h("i")).style.width = Math.round((L.idx / L.steps.length) * 100) + "%";
    top.appendChild(prog);
    top.appendChild(h("div", "heart-mini", "❤️ " + P.hearts));
    root.appendChild(top);

    const step = L.steps[L.idx];
    const s = h("div", "step");
    root.appendChild(s);
    s.appendChild(h("div", "pill info", "🎯 Trening"));
    s.appendChild(h("div", "prompt-q", step.q));
    const foot = h("div"); foot.id = "lf"; foot.style.marginTop = "14px";

    const opts = [];
    step.options.forEach((o, i) => {
      const b = h("button", "opt", o);
      b.onclick = () => {
        if (L.stepDone) return;
        opts.forEach((x) => x.classList.remove("sel"));
        b.classList.add("sel"); L.sel = i; chk.disabled = false;
      };
      opts.push(b); s.appendChild(b);
    });
    const chk = h("button", "btn", "Sprawdź"); chk.disabled = true;
    chk.onclick = () => {
      if (L.sel == null || L.stepDone) return;
      L.stepDone = true;
      const ok = L.sel === step.answer;
      opts.forEach((x, i) => {
        x.disabled = true;
        if (i === step.answer) x.classList.add("correct");
        else if (i === L.sel) x.classList.add("wrong");
      });
      if (ok) { L.correct++; P.xp += 2; gainHeart(1); }
      else { L.wrong++; }
      save();
      practiceBanner(ok, step.explain || "");
    };
    foot.appendChild(chk);
    root.appendChild(foot);
  }

  function practiceBanner(ok, html) {
    const fb = h("div", "feedback " + (ok ? "ok" : "bad"));
    const inner = h("div", "fb-inner");
    inner.appendChild(h("div", "fb-title", ok ? "✅ Dobrze! +2 XP ❤️" : "❌ Prawie"));
    if (html) inner.appendChild(h("div", "fb-text", html));
    const cont = h("button", "btn", "Dalej");
    cont.onclick = () => { L.idx++; L.sel = null; L.stepDone = false; renderPracticeStep(); };
    inner.appendChild(cont);
    fb.appendChild(inner);
    document.body.appendChild(fb);
    cont.focus();
  }

  function practiceSummary() {
    root.innerHTML = "";
    confetti();
    const c = h("div", "card center");
    c.appendChild(h("div", "big-emoji", "🎯"));
    c.appendChild(h("h1", null, "Trening ukończony"));
    c.appendChild(h("div", "stat-grid"))
      .append(statCard(L.correct + "/" + L.steps.length, "poprawne", "var(--green-d)"),
        statCard("+" + (L.correct * 2), "XP", "var(--gold-d)"));
    const b = h("button", "btn", "Gotowe");
    b.onclick = () => setView("path");
    c.appendChild(b);
    root.appendChild(c);
    tabbar.classList.remove("hidden");
  }

  /* ======================================================================
     WIDOK: słownik
     ====================================================================== */
  function renderGlossary() {
    root.innerHTML = "";
    root.appendChild(topbar());
    root.appendChild(h("h1", null, "📖 Słownik"));
    root.appendChild(h("p", "muted", "Pojęcia z kursu — atak i obrona w jednym miejscu."));

    const search = document.createElement("input");
    search.placeholder = "Szukaj pojęcia…";
    search.style.cssText =
      "width:100%;padding:12px 14px;border-radius:12px;border:2px solid var(--line);" +
      "background:var(--card);color:var(--ink);font-weight:700;font-family:inherit;margin:8px 0 4px";
    root.appendChild(search);
    const listWrap = h("div");
    root.appendChild(listWrap);

    function draw(filter) {
      listWrap.innerHTML = "";
      const f = (filter || "").toLowerCase();
      GLOSSARY.filter((g) => !f || g.term.toLowerCase().includes(f) || g.def.toLowerCase().includes(f) ||
        g.tags.join(" ").includes(f)).forEach((g) => {
          const it = h("div", "list-item");
          it.appendChild(h("h3", null, g.term));
          it.appendChild(h("p", "muted", g.def));
          const tr = h("div", "tag-row");
          g.tags.forEach((t) => tr.appendChild(h("span", "pill info", t)));
          it.appendChild(tr);
          listWrap.appendChild(it);
        });
    }
    search.oninput = () => draw(search.value);
    draw("");
    root.appendChild(h("div", "spacer"));
  }

  /* ======================================================================
     WIDOK: profil
     ====================================================================== */
  function renderProfile() {
    root.innerHTML = "";
    root.appendChild(topbar());
    const hero = h("div", "profile-hero");
    hero.appendChild(h("div", "avatar", "🕵️"));
    hero.appendChild(h("h1", null, "Etyczny haker"));
    hero.appendChild(h("div", "muted", "Poziom " + level() + " · " + P.xp + " XP"));
    root.appendChild(hero);

    const grid = h("div", "stat-grid");
    grid.appendChild(statCard("🔥 " + (P.streak || 0), "seria dni", "var(--orange)"));
    grid.appendChild(statCard("⭐ " + P.xp, "łącznie XP", "var(--gold-d)"));
    grid.appendChild(statCard(Object.keys(P.done).length + "/" + FLAT.length, "lekcje", "var(--green-d)"));
    grid.appendChild(statCard("❤️ " + P.hearts, "serca", "var(--red)"));
    root.appendChild(grid);

    root.appendChild(h("h2", null, "Odznaki"));
    const bg = h("div", "badge-grid card");
    BADGES.forEach((b) => {
      const earned = b.test(P);
      const el2 = h("div", "badge" + (earned ? " earned" : ""));
      el2.appendChild(h("div", "b-ico", earned ? b.ico : "🔒"));
      el2.appendChild(h("div", "b-name", b.name));
      bg.appendChild(el2);
    });
    root.appendChild(bg);

    root.appendChild(h("div", "ethics",
      "⚖️ <b>Używaj wiedzy legalnie.</b> Testuj wyłącznie systemy, na które masz zgodę. " +
      "Nieautoryzowany dostęp do cudzych systemów jest przestępstwem. Ten kurs uczy obrony."));

    const reset = h("button", "btn ghost", "Zresetuj postęp");
    reset.onclick = () => {
      if (confirm("Na pewno wyczyścić cały postęp?")) { P = defState(); save(); setView("path"); toast("Postęp wyczyszczony"); }
    };
    root.appendChild(reset);
    root.appendChild(h("div", "spacer"));
    root.appendChild(h("div", "center muted", "HackLingo · nauka bezpieczeństwa webowego · wszystko działa offline"));
  }

  /* ---------- główny render + zakładki ---------- */
  function render() {
    syncHearts();
    if (view !== "path" || !L) tabbar.classList.remove("hidden");
    [].forEach.call(document.querySelectorAll(".tab"), (t) =>
      t.classList.toggle("active", t.dataset.view === view));
    if (view === "path") renderPath();
    else if (view === "practice") renderPractice();
    else if (view === "glossary") renderGlossary();
    else if (view === "profile") renderProfile();
    window.scrollTo(0, 0);
  }

  [].forEach.call(document.querySelectorAll(".tab"), (t) => {
    t.onclick = () => setView(t.dataset.view);
  });

  render();
})();
