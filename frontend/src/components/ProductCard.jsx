import { Link } from 'react-router-dom'
import { formatPrice, stockStatus } from '../utils'

/** Compact stock indicator: colour is never the only signal (the label says it too). */
export function StockBadge({ quantity }) {
  const s = stockStatus(quantity)
  return <span className={`stock-badge ${s.tone}`}>{s.label}</span>
}

/**
 * One product in the catalogue grid. `to` is the detail route, `imageUrl` the image endpoint for this catalogue.
 * Shows a neutral placeholder when the product has no image (nothing is invented).
 */
export default function ProductCard({ product, to, imageUrl }) {
  const hasImage = Boolean(product.imageName || product.imageType)
  return (
    <Link to={to} className="product-card" aria-label={`${product.name}, ${formatPrice(product.price)}`}>
      <div className="product-media">
        {hasImage ? (
          <img
            src={imageUrl}
            alt=""
            loading="lazy"
            onError={(e) => {
              e.currentTarget.style.display = 'none'
              e.currentTarget.parentElement.classList.add('no-image')
            }}
          />
        ) : null}
        <span className="media-placeholder" aria-hidden="true">{(product.name || '?').charAt(0).toUpperCase()}</span>
      </div>
      <div className="product-body">
        {product.category && <span className="product-category">{product.category}</span>}
        <h3 className="product-name">{product.name}</h3>
        <div className="product-meta">
          <span className="product-price">{formatPrice(product.price)}</span>
          <StockBadge quantity={product.quantity} />
        </div>
      </div>
    </Link>
  )
}
