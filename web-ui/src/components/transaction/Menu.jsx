import {Link} from "react-router-dom";
import UserService from "../../services/UserService";

const Menu = () => (
  <nav className="navbar navbar-expand bg-body-tertiary py-3 mb-5">
    <div className="container-fluid">
      <Link className="navbar-brand" to="/">Transaction UI</Link>
      <div className="collapse navbar-collapse">
        <ul className="navbar-nav me-auto">
          <li className="nav-item"><Link to="/" className="nav-link">List</Link></li>
          <li className="nav-item"><Link to="/transactions/new" className="nav-link">New Transaction</Link></li>
        </ul>
        <div className="d-flex align-items-center">
          <div className="navbar-text mx-1">
            Signed in as <b>{UserService.getUsername()}</b>
          </div>
          <button className="btn btn-sm btn-success mx-1"
                  onClick={() => UserService.doLogout({
                    redirectUri: import.meta.env.VITE_SELF_URL
                  })}>
            Logout
          </button>
        </div>
      </div>
    </div>
  </nav>
)

export default Menu
