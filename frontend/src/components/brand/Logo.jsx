import { NOVA_LOGO_SRC } from '../../assets/novaLogoData';

export function Logo({ inverse = false, compact = false, background = inverse }) {
  return (
    <div className={`inline-flex items-center ${background ? 'rounded-md bg-white px-3 py-2' : ''}`}>
      <img
        src={NOVA_LOGO_SRC}
        alt="NOVA Bank"
        className={`${compact ? 'h-9 w-11 object-cover object-left' : 'h-9 w-auto max-w-[148px] object-contain'} ${inverse && !background ? 'brightness-0 invert' : ''}`}
      />
    </div>
  );
}
