const picker = document.getElementById('file-input');
picker.addEventListener('click', event => event.stopPropagation());
document.getElementById('drop-zone').addEventListener('keydown', event => {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    picker.click();
  }
});
document.querySelectorAll('.tab-btn').forEach(button => {
  button.addEventListener('click', () => {
    document.querySelectorAll('.tab-btn').forEach(other => {
      other.setAttribute('aria-pressed', String(other === button));
    });
  });
});
document.querySelector('.benchmark-cue').addEventListener('click', event => {
  event.preventDefault();
  const section = document.getElementById('benchmarks');
  section.scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' });
  section.focus({ preventScroll: true });
});
