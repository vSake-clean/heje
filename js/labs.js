/* =========================================================================
   HackLingo – symulowane laboratoria podatności
   WAŻNE: nic nie jest wykonywane naprawdę. Payloady są WYKRYWANE wzorcami
   i SYMULOWANE wizualnie. Żaden wstrzyknięty skrypt nie uruchamia się,
   żadna komenda nie trafia do systemu. To bezpieczny poligon do nauki.
   ========================================================================= */

/* ---------- pomocniki ---------- */
function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
}
function esc(s) {
  return String(s).replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c])
  );
}
function term(parent, placeholder) {
  const t = el("div", "terminal");
  parent.appendChild(t);
  const PH = placeholder || "// wynik pojawi się tutaj…";
  function addPH() { const p = el("div", "dim", PH); p.dataset.ph = "1"; t.appendChild(p); }
  addPH();
  return {
    node: t,
    line(txt, cls) {
      const ph = t.querySelector("[data-ph]"); if (ph) ph.remove();
      const l = el("div", cls || null, txt);
      t.appendChild(l);
      t.scrollTop = t.scrollHeight;
      return l;
    },
    clear() { t.textContent = ""; addPH(); }
  };
}

/* ---------- rejestr laboratoriów ---------- */
const LABS = {};

/* ====================== SQLi: obejście logowania ====================== */
LABS["sqli-login"] = function (step, ctx) {
  const wrap = el("div", "lab");
  wrap.appendChild(el("div", "lab-url", "POST /login   →   SELECT * FROM users WHERE login='…' AND haslo='…'"));

  const f1 = el("div", "field");
  f1.appendChild(el("label", null, "Login"));
  const login = el("input"); login.placeholder = "np. admin"; login.value = "";
  f1.appendChild(login);

  const f2 = el("div", "field");
  f2.appendChild(el("label", null, "Hasło"));
  const pass = el("input"); pass.placeholder = "nie znasz go…"; pass.type = "text";
  f2.appendChild(pass);

  wrap.appendChild(f1);
  wrap.appendChild(f2);

  const btn = el("button", "btn blue", "Zaloguj");
  wrap.appendChild(btn);
  const out = term(wrap);

  function sqliHit(u, p) {
    const t = u + " :: " + p;
    const taut =
      /'\s*or\s*('?)(\w+)\1\s*=\s*('?)\2\3/i.test(t) ||
      /'\s*or\s*'?1'?\s*=\s*'?1/i.test(t) ||
      /'\s*or\s*1\s*=\s*1/i.test(t);
    const cmt = /'\s*(--|#)/.test(t);
    return taut || cmt;
  }

  btn.onclick = () => {
    const u = login.value, p = pass.value;
    out.clear();
    out.line("SQL> SELECT * FROM users WHERE login='" + u + "' AND haslo='" + p + "'", "sys");
    if (!u && !p) { out.line("Podaj dane albo… spróbuj czegoś sprytniejszego ;)", "dim"); return; }
    if (sqliHit(u, p)) {
      out.line("[1 wiersz] id=1  login=admin  rola=ADMIN", "ok");
      out.line("✔ Zalogowano jako ADMIN — bez znajomości hasła!", "ok");
      ctx.onSuccess({
        message: "Zapytanie zwróciło TRUE dla każdego wiersza — warunek na hasło przestał mieć znaczenie.",
        defense: "Obrona: zapytania parametryzowane (prepared statements) — dane wejściowe nigdy nie są traktowane jako kod SQL."
      });
    } else if (u || p) {
      out.line("Błędny login lub hasło.", "err");
      out.line("(Serwer po prostu wkleił Twój tekst między apostrofy — pomyśl, jak z nich wyjść.)", "dim");
    }
  };
  return wrap;
};

/* ====================== SQLi: UNION / wyciek ====================== */
LABS["sqli-union"] = function (step, ctx) {
  const wrap = el("div", "lab");
  wrap.appendChild(el("div", "lab-url", "GET /szukaj?q=…   →   SELECT nazwa, cena FROM produkty WHERE nazwa LIKE '%…%'"));
  const f = el("div", "field");
  f.appendChild(el("label", null, "Szukaj produktu"));
  const q = el("input"); q.placeholder = "np. laptop";
  f.appendChild(q);
  wrap.appendChild(f);
  const btn = el("button", "btn blue", "Szukaj");
  wrap.appendChild(btn);
  const out = term(wrap);

  btn.onclick = () => {
    const s = q.value;
    out.clear();
    out.line("SQL> SELECT nazwa, cena FROM produkty WHERE nazwa LIKE '%" + s + "%'", "sys");
    const hit = /union\s+select/i.test(s) && /(users|login|haslo|hasl|pass|user)/i.test(s);
    if (hit) {
      out.line("nazwa              | cena", "dim");
      out.line("admin              | S3cr3t_P@ss!", "ok");
      out.line("jan.kowalski       | wiosna2023", "ok");
      out.line("sklep_bot          | qwerty12345", "ok");
      out.line("✔ Zamiast produktów baza zwróciła loginy i hasła z tabeli users!", "ok");
      ctx.onSuccess({
        message: "UNION SELECT dokleił kolumny z innej tabeli do wyników zapytania.",
        defense: "Obrona: parametryzacja + minimalne uprawnienia konta bazy (nie powinno mieć dostępu do tabeli users z poziomu wyszukiwarki) + hasła trzymane jako hash."
      });
    } else if (/union/i.test(s)) {
      out.line("Prawie! Masz UNION, ale wskaż kolumny i tabelę users, i zakomentuj resztę ( -- ).", "dim");
    } else if (s) {
      out.line("Brak wyników dla „" + s + "”.", "dim");
      out.line("(To pole wkleja Twój tekst do zapytania — spróbuj je 'rozszerzyć'.)", "dim");
    }
  };
  return wrap;
};

/* ====================== XSS: księga gości ====================== */
function formatBasic(text) {
  // Bezpiecznie renderuje TYLKO proste tagi formatujące (b/i/u) jako prawdziwe
  // elementy — żeby pokazać, że HTML jest interpretowany. Wszystko inne staje
  // się zwykłym tekstem (textNode = brak wykonania).
  const frag = document.createDocumentFragment();
  const parts = text.split(/(<\/?[biu]>)/i);
  const stack = [frag];
  parts.forEach((part) => {
    const t = part.toLowerCase();
    if (/^<[biu]>$/.test(t)) {
      const e = document.createElement(t[1]);
      stack[stack.length - 1].appendChild(e);
      stack.push(e);
    } else if (/^<\/[biu]>$/.test(t)) {
      if (stack.length > 1) stack.pop();
    } else if (part) {
      stack[stack.length - 1].appendChild(document.createTextNode(part));
    }
  });
  return frag;
}

LABS["xss"] = function (step, ctx) {
  const wrap = el("div", "lab");
  wrap.appendChild(el("div", "lab-url", "Księga gości — wyświetla komentarze gości"));

  const f1 = el("div", "field");
  f1.appendChild(el("label", null, "Podpis"));
  const name = el("input"); name.value = "gość"; f1.appendChild(name);

  const f2 = el("div", "field");
  f2.appendChild(el("label", null, "Komentarz"));
  const msg = el("textarea"); msg.rows = 2; msg.placeholder = "napisz coś…"; f2.appendChild(msg);

  wrap.appendChild(f1); wrap.appendChild(f2);
  const btn = el("button", "btn", "Opublikuj"); btn.classList.add("blue");
  wrap.appendChild(btn);

  const box = el("div", "render-box");
  const seed = el("div", "c-item");
  seed.appendChild(el("b", null, "admin: "));
  seed.appendChild(document.createTextNode("Witaj w naszej księdze gości!"));
  box.appendChild(seed);
  wrap.appendChild(box);

  const payloadRe = /<\s*script\b|on\w+\s*=|javascript:\s*|<\s*img\b|<\s*svg\b|<\s*iframe\b|<\s*body\b/i;

  btn.onclick = () => {
    const c = msg.value;
    if (!c.trim()) return;
    const item = el("div", "c-item");
    item.appendChild(el("b", null, (name.value || "gość") + ": "));

    if (payloadRe.test(c)) {
      const fired = el("div", "xss-fire", "💥 alert(1) — skrypt wykonał się w przeglądarce! (symulacja)");
      item.appendChild(fired);
      const raw = el("div"); raw.style.cssText = "font-family:var(--mono);font-size:11px;opacity:.6";
      raw.textContent = c;
      item.appendChild(raw);
      box.appendChild(item);
      ctx.onSuccess({
        message: "Strona wstawiła Twój tekst do HTML bez kodowania — przeglądarka potraktowała go jak kod.",
        defense: "Obrona: koduj znaki < > & \" przy wyświetlaniu (output encoding). Dodatkowo nagłówek Content-Security-Policy blokuje nieautoryzowane skrypty."
      });
    } else {
      item.appendChild(formatBasic(c));
      box.appendChild(item);
      if (/<\/?[biu]>/i.test(c)) {
        const note = el("div", "dim"); note.style.cssText = "font-size:11px;margin-top:2px";
        note.textContent = "↑ zauważ: Twój HTML został zinterpretowany (formatowanie zadziałało). Skoro HTML działa, zadziała też <script>…";
        item.appendChild(note);
      }
    }
    msg.value = "";
  };
  return wrap;
};

/* ====================== Brute-force / słownik ====================== */
LABS["brute"] = function (step, ctx) {
  const wrap = el("div", "lab");
  wrap.appendChild(el("div", "lab-url", "POST /login  (brak limitu prób logowania)"));

  const common = ["123456", "password", "qwerty", "111111", "iloveyou",
    "password123", "abc123", "letmein", "admin", "dragon"];
  const REAL = "password123";

  const f1 = el("div", "field");
  f1.appendChild(el("label", null, "Login"));
  const u = el("input"); u.value = "admin"; u.readOnly = true; f1.appendChild(u);
  const f2 = el("div", "field");
  f2.appendChild(el("label", null, "Hasło"));
  const p = el("input"); p.placeholder = "zgadnij…"; f2.appendChild(p);
  wrap.appendChild(f1); wrap.appendChild(f2);

  const row = el("div"); row.style.cssText = "display:flex;gap:10px";
  const btn = el("button", "btn blue", "Zaloguj");
  const dict = el("button", "btn gold", "Atak słownikowy");
  row.appendChild(btn); row.appendChild(dict);
  wrap.appendChild(row);
  const out = term(wrap);

  let solved = false;
  function win(pw, tries) {
    if (solved) return;
    solved = true;
    out.line("✔ Hasło odgadnięte: „" + pw + "” po " + tries + " próbach", "ok");
    ctx.onSuccess({
      message: "Konto używało hasła z listy najczęstszych, a serwer nie blokował kolejnych prób.",
      defense: "Obrona: rate limiting / blokada konta po kilku błędach, wymuszanie silnych haseł i 2FA (drugi składnik)."
    });
  }

  btn.onclick = () => {
    const val = p.value;
    if (val === REAL) win(val, 1);
    else { out.line("admin : „" + val + "” → odrzucone", "err"); }
  };

  dict.onclick = () => {
    out.clear();
    out.line("[*] Start ataku słownikowego (brak rate-limitingu = każda próba darmowa)…", "sys");
    let i = 0;
    const timer = setInterval(() => {
      if (i >= common.length || solved) { clearInterval(timer); return; }
      const pw = common[i];
      if (pw === REAL) {
        out.line("admin : „" + pw + "” → ✔ TRAFIONE", "ok");
        clearInterval(timer);
        win(pw, i + 1);
      } else {
        out.line("admin : „" + pw + "” → odrzucone", "dim");
      }
      i++;
    }, 350);
  };
  return wrap;
};

/* ====================== IDOR ====================== */
LABS["idor"] = function (step, ctx) {
  const wrap = el("div", "lab");
  const urlNode = el("div", "lab-url", "GET /api/profil?user_id=1337   (zalogowany jako #1337)");
  wrap.appendChild(urlNode);

  const f = el("div", "field");
  f.appendChild(el("label", null, "user_id"));
  const id = el("input"); id.value = "1337"; id.inputMode = "numeric"; f.appendChild(id);
  wrap.appendChild(f);
  const btn = el("button", "btn blue", "Pobierz profil");
  wrap.appendChild(btn);
  const out = term(wrap);

  function profile(n) {
    if (n === "1337") return { imie: "To Ty (Gość)", rola: "user", email: "ty@example.com", sekret: null };
    if (n === "1") return { imie: "Anna Admin", rola: "ADMIN", email: "anna@firma.pl", sekret: "API_KEY=sk-live-9f3a…  🔑" };
    return { imie: "Użytkownik #" + n, rola: "user", email: "user" + n + "@example.com", sekret: "notatka prywatna #" + n };
  }

  btn.onclick = () => {
    const n = (id.value || "").trim();
    if (!/^\d+$/.test(n)) { out.clear(); out.line("user_id musi być liczbą.", "err"); return; }
    urlNode.textContent = "GET /api/profil?user_id=" + n + "   (zalogowany jako #1337)";
    const pr = profile(n);
    out.clear();
    out.line("200 OK — profil #" + n, "sys");
    out.line("imię:  " + pr.imie);
    out.line("rola:  " + pr.rola, pr.rola === "ADMIN" ? "ok" : null);
    out.line("email: " + pr.email);
    if (pr.sekret) out.line("sekret: " + pr.sekret, "ok");

    if (n !== "1337") {
      out.line("✔ Serwer oddał CUDZE dane — nie sprawdził, czy to Twoje konto!", "ok");
      ctx.onSuccess({
        message: "Zmieniłeś tylko numer w adresie, a serwer zwrócił dane innego użytkownika.",
        defense: "Obrona: na serwerze sprawdzaj, czy zalogowany użytkownik ma prawo do danego obiektu (autoryzacja), zamiast ufać identyfikatorowi z żądania."
      });
    } else {
      out.line("(To Twój własny profil. A gdybyś wpisał inny numer?)", "dim");
    }
  };
  return wrap;
};

/* ====================== Command Injection ====================== */
LABS["cmd"] = function (step, ctx) {
  const wrap = el("div", "lab");
  wrap.appendChild(el("div", "lab-url", "Panel diagnostyczny  →  system(\"ping -c1 \" + wpis)"));
  const f = el("div", "field");
  f.appendChild(el("label", null, "Host do pingowania"));
  const host = el("input"); host.placeholder = "np. 8.8.8.8"; f.appendChild(host);
  wrap.appendChild(f);
  const btn = el("button", "btn blue", "Ping");
  wrap.appendChild(btn);
  const out = term(wrap);

  btn.onclick = () => {
    const v = host.value.trim();
    out.clear();
    if (!v) { out.line("Podaj host.", "dim"); return; }
    out.line("$ ping -c1 " + v, "sys");
    const base = v.split(/[;&|`]|\$\(/)[0].trim() || "host";
    out.line("PING " + base + ": 56 bajtów", "dim");
    out.line("64 bytes from " + base + ": icmp_seq=1 ttl=117 time=12.3 ms", "dim");

    const inj = /[;&|`]|\$\(/.test(v);
    if (inj) {
      const low = v.toLowerCase();
      out.line("", null);
      if (/\b(id|whoami)\b/.test(low)) {
        out.line("$ (wstrzyknięta komenda)", "sys");
        out.line("uid=0(root) gid=0(root) groups=0(root)", "ok");
      } else if (/\b(ls|dir)\b/.test(low)) {
        out.line("app.js  config.php  .env  backup.sql", "ok");
      } else if (/\bcat\b/.test(low)) {
        out.line("DB_PASSWORD=sup3r_s3cret", "ok");
      } else if (/\buname\b/.test(low)) {
        out.line("Linux web01 6.1.0 x86_64 GNU/Linux", "ok");
      } else {
        out.line("[wstrzyknięta komenda wykonana] ✔", "ok");
      }
      out.line("✔ Twój tekst wyszedł poza argument ping — powłoka wykonała dodatkową komendę!", "ok");
      ctx.onSuccess({
        message: "Znak łączący komendy (; && | ` $()) sprawił, że powłoka uruchomiła Twoją komendę obok ping.",
        defense: "Obrona: nie buduj poleceń ze sklejanych stringów. Przekazuj argumenty jako listę do bezpiecznego API, waliduj wejście (allowlist) i działaj z minimalnymi uprawnieniami."
      });
    } else {
      out.line("1 packets transmitted, 1 received, 0% packet loss", "dim");
      out.line("(Czysty ping. A gdyby dokleić do hosta coś jeszcze?)", "dim");
    }
  };
  return wrap;
};

/* ====================== Path Traversal ====================== */
LABS["path"] = function (step, ctx) {
  const wrap = el("div", "lab");
  wrap.appendChild(el("div", "lab-url", "GET /download?file=…   →   read(\"/var/www/files/\" + file)"));
  const f = el("div", "field");
  f.appendChild(el("label", null, "Nazwa pliku"));
  const file = el("input"); file.value = "raport.pdf"; f.appendChild(file);
  wrap.appendChild(f);
  const btn = el("button", "btn blue", "Pobierz");
  wrap.appendChild(btn);
  const out = term(wrap);

  btn.onclick = () => {
    const v = file.value.trim();
    out.clear();
    const full = "/var/www/files/" + v;
    out.line("open(\"" + full + "\")", "sys");
    const trav = /(\.\.[\/\\])/.test(v) || /%2e%2e/i.test(v);
    const passwd = /etc[\/\\]passwd/i.test(v);
    if (passwd && trav) {
      out.line("root:x:0:0:root:/root:/bin/bash", "ok");
      out.line("daemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin", "ok");
      out.line("www-data:x:33:33:www-data:/var/www:/usr/sbin/nologin", "ok");
      out.line("✔ Wyszedłeś poza /var/www/files i odczytałeś plik systemowy!", "ok");
      ctx.onSuccess({
        message: "Sekwencje ../ cofnęły Cię do korzenia systemu plików i pozwoliły odczytać /etc/passwd.",
        defense: "Obrona: zmień ścieżkę na kanoniczną i sprawdź, czy nadal leży w dozwolonym folderze. Najlepiej mapuj dozwolone pliki po stałych identyfikatorach."
      });
    } else if (trav) {
      out.line("… wyszedłeś z katalogu plików! (spróbuj dotrzeć do /etc/passwd)", "ok");
      ctx.onSuccess({
        message: "Użyłeś ../ i opuściłeś przeznaczony katalog — to już path traversal.",
        defense: "Obrona: normalizacja ścieżki + sprawdzenie, czy wynik leży w dozwolonym folderze (allowlista)."
      });
    } else if (v) {
      out.line("Pobrano „" + v + "” z folderu plików. (symulacja)", "dim");
      out.line("(Pliki czytane są względem /var/www/files/ — jak wyjść wyżej?)", "dim");
    }
  };
  return wrap;
};
