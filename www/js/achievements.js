/* Odkrywca: odznaki. Każda ma prosty, sprawdzalny próg. */
(function (global) {
  'use strict';

  const km2 = 1e6;
  const area = v => (v / km2).toFixed(2).replace('.', ',') + ' km²';
  const km = v => (v / 1000).toFixed(1).replace('.', ',') + ' km';
  const num = v => String(Math.floor(v));

  // value: bieżąca wartość, goal: próg, fmt: jak pokazać postęp
  const A = [
    { id: 'area1',     name: 'Najbliższa okolica', desc: 'Odkryj 0,5 km²',                         value: s => s.area,        goal: 0.5 * km2, fmt: area },
    { id: 'area5',     name: 'Dzielnica',          desc: 'Odkryj 5 km²',                           value: s => s.area,        goal: 5 * km2,   fmt: area },
    { id: 'area25',    name: 'Miasto',             desc: 'Odkryj 25 km²',                          value: s => s.area,        goal: 25 * km2,  fmt: area },
    { id: 'area100',   name: 'Region',             desc: 'Odkryj 100 km²',                         value: s => s.area,        goal: 100 * km2, fmt: area },
    { id: 'dist5',     name: 'Pierwsze kilometry', desc: 'Przebądź 5 km',                          value: s => s.distance,    goal: 5000,      fmt: km },
    { id: 'dist42',    name: 'Maraton',            desc: 'Przebądź łącznie 42,2 km',               value: s => s.distance,    goal: 42195,     fmt: km },
    { id: 'dist500',   name: 'Pięćset',            desc: 'Przebądź łącznie 500 km',                value: s => s.distance,    goal: 500000,    fmt: km },
    { id: 'far50',     name: 'Za horyzontem',      desc: 'Bądź 50 km od miejsca pierwszego zapisu', value: s => s.maxFromHome, goal: 50000,     fmt: km },
    { id: 'far1000',   name: 'Daleko od domu',     desc: 'Bądź 1000 km od miejsca pierwszego zapisu', value: s => s.maxFromHome, goal: 1e6,     fmt: km },
    { id: 'city10',    name: 'Dziesięć miejscowości', desc: 'Odwiedź 10 miejscowości',             value: s => s.cities,      goal: 10,        fmt: num },
    { id: 'city50',    name: 'Pięćdziesiąt miejscowości', desc: 'Odwiedź 50 miejscowości',         value: s => s.cities,      goal: 50,        fmt: num },
    { id: 'country2',  name: 'Za granicą',         desc: 'Odwiedź drugi kraj',                     value: s => s.countries,   goal: 2,         fmt: num },
    { id: 'country10', name: 'Dziesięć krajów',    desc: 'Odwiedź 10 krajów',                      value: s => s.countries,   goal: 10,        fmt: num },
    { id: 'streak7',   name: 'Tydzień',            desc: 'Ruszaj się 7 dni z rzędu',               value: s => s.bestStreak,  goal: 7,         fmt: num },
    { id: 'streak30',  name: 'Miesiąc',            desc: 'Ruszaj się 30 dni z rzędu',              value: s => s.bestStreak,  goal: 30,        fmt: num },
    { id: 'night',     name: 'Nocą',               desc: 'Odkryj coś między północą a 4:00',       value: s => s.nightOwl ? 1 : 0,  goal: 1 },
    { id: 'early',     name: 'O świcie',           desc: 'Odkryj coś między 5:00 a 7:00',          value: s => s.earlyBird ? 1 : 0, goal: 1 }
  ];

  global.ACHIEVEMENTS = A;
})(window);
