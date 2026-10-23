/* Odkrywca – odznaki do zdobycia */
(function (global) {
  'use strict';

  const km2 = 1e6;
  const A = [
    { id: 'first',    icon: '👣', name: 'Pierwszy krok',      desc: 'Odsłoń pierwsze miejsce na mapie',            test: s => s.points >= 1 },
    { id: 'area1',    icon: '🌱', name: 'Mały ogródek',       desc: 'Odkryj 0,5 km² świata',                       test: s => s.area >= 0.5 * km2, progress: s => s.area / (0.5 * km2) },
    { id: 'area5',    icon: '🏘️', name: 'Moja okolica',       desc: 'Odkryj 5 km²',                                test: s => s.area >= 5 * km2, progress: s => s.area / (5 * km2) },
    { id: 'area25',   icon: '🏙️', name: 'Pan na mieście',     desc: 'Odkryj 25 km²',                               test: s => s.area >= 25 * km2, progress: s => s.area / (25 * km2) },
    { id: 'area100',  icon: '🗺️', name: 'Kartograf',          desc: 'Odkryj 100 km²',                              test: s => s.area >= 100 * km2, progress: s => s.area / (100 * km2) },
    { id: 'area1000', icon: '🌋', name: 'Władca krain',       desc: 'Odkryj 1000 km²',                             test: s => s.area >= 1000 * km2, progress: s => s.area / (1000 * km2) },
    { id: 'dist5',    icon: '🚶', name: 'Spacerowicz',        desc: 'Przejdź 5 km z włączonym odkrywaniem',        test: s => s.distance >= 5000, progress: s => s.distance / 5000 },
    { id: 'dist42',   icon: '🏃', name: 'Maratończyk',        desc: 'Pokonaj łącznie 42,195 km',                   test: s => s.distance >= 42195, progress: s => s.distance / 42195 },
    { id: 'dist500',  icon: '🚴', name: 'Długodystansowiec',  desc: 'Pokonaj łącznie 500 km',                      test: s => s.distance >= 500000, progress: s => s.distance / 500000 },
    { id: 'dist5000', icon: '🚂', name: 'W drodze',           desc: 'Pokonaj łącznie 5000 km',                     test: s => s.distance >= 5e6, progress: s => s.distance / 5e6 },
    { id: 'far50',    icon: '🧭', name: 'Za horyzontem',      desc: 'Bądź 50 km od miejsca pierwszego odkrycia',   test: s => s.maxFromHome >= 50000, progress: s => s.maxFromHome / 50000 },
    { id: 'far1000',  icon: '✈️', name: 'Daleka podróż',      desc: 'Bądź 1000 km od miejsca pierwszego odkrycia', test: s => s.maxFromHome >= 1e6, progress: s => s.maxFromHome / 1e6 },
    { id: 'city1',    icon: '🏡', name: 'Lokalny patriota',   desc: 'Odkryj pierwszą miejscowość',                 test: s => s.cities >= 1 },
    { id: 'city10',   icon: '🏰', name: 'Wędrowny kupiec',    desc: 'Odwiedź 10 miejscowości',                     test: s => s.cities >= 10, progress: s => s.cities / 10 },
    { id: 'city50',   icon: '🗼', name: 'Kolekcjoner miast',  desc: 'Odwiedź 50 miejscowości',                     test: s => s.cities >= 50, progress: s => s.cities / 50 },
    { id: 'country2', icon: '🛂', name: 'Przez granicę',      desc: 'Odwiedź 2 kraje',                             test: s => s.countries >= 2, progress: s => s.countries / 2 },
    { id: 'country5', icon: '🌍', name: 'Obieżyświat',        desc: 'Odwiedź 5 krajów',                            test: s => s.countries >= 5, progress: s => s.countries / 5 },
    { id: 'country15',icon: '🪐', name: 'Obywatel świata',    desc: 'Odwiedź 15 krajów',                           test: s => s.countries >= 15, progress: s => s.countries / 15 },
    { id: 'streak3',  icon: '🔥', name: 'Rozgrzewka',         desc: 'Odkrywaj 3 dni z rzędu',                      test: s => s.bestStreak >= 3, progress: s => s.bestStreak / 3 },
    { id: 'streak7',  icon: '📅', name: 'Tydzień przygód',    desc: 'Odkrywaj 7 dni z rzędu',                      test: s => s.bestStreak >= 7, progress: s => s.bestStreak / 7 },
    { id: 'streak30', icon: '🏅', name: 'Niezłomny',          desc: 'Odkrywaj 30 dni z rzędu',                     test: s => s.bestStreak >= 30, progress: s => s.bestStreak / 30 },
    { id: 'day1km',   icon: '⚡', name: 'Dzień odkrywcy',     desc: 'Odsłoń 1 km² jednego dnia',                   test: s => s.bestDayArea >= km2, progress: s => s.bestDayArea / km2 },
    { id: 'night',    icon: '🦉', name: 'Nocny marek',        desc: 'Odkrywaj między północą a 4:00',              test: s => s.nightOwl },
    { id: 'early',    icon: '🐦', name: 'Ranny ptaszek',      desc: 'Odkrywaj między 5:00 a 7:00',                 test: s => s.earlyBird },
    { id: 'pin1',     icon: '📍', name: 'Pamiątka',           desc: 'Dodaj pierwsze wspomnienie na mapie',         test: s => s.pins >= 1 },
    { id: 'pin10',    icon: '📔', name: 'Kronikarz',          desc: 'Dodaj 10 wspomnień',                          test: s => s.pins >= 10, progress: s => s.pins / 10 },
    { id: 'level10',  icon: '⭐', name: 'Doświadczony',       desc: 'Osiągnij 10. poziom',                         test: s => s.level >= 10, progress: s => s.level / 10 },
    { id: 'level25',  icon: '🌟', name: 'Mistrz odkryć',      desc: 'Osiągnij 25. poziom',                         test: s => s.level >= 25, progress: s => s.level / 25 }
  ];

  global.ACHIEVEMENTS = A;
})(window);
